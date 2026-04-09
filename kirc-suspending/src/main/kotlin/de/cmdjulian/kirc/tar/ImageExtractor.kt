package de.cmdjulian.kirc.tar

import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.impl.serialization.deserialize
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.image.DockerImageConfigV1
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.image.OciImageConfigV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.inputStream
import kotlin.io.path.pathString
import kotlinx.io.files.Path as KotlinPath

/**
 * Utility object to parse docker image tar archives.
 */
object ImageExtractor {

    // --- PUBLIC API ---

    /**
     * Performs a single sequential pass through [input], spilling blobs to [tempDirectory], then resolves and
     * returns a fully-parsed [ContainerImageTar].
     *
     * [input] - the tar input stream to read from (consumed exactly once)
     * [tempDirectory] - directory where blob files are spilled during parsing
     */
    suspend fun parse(input: InputStream, tempDirectory: Path): ContainerImageTar = input.use { stream ->
        val blobs = mutableMapOf<Digest, Path>()
        val scan = TarArchiveInputStream(stream).use { tar ->
            tar.scanEntries { digest, entry -> blobs[digest] = tar.processBlobEntry(entry, tempDirectory) }
        }

        val (processedIndex, resolvedManifests) = resolveManifestsAndBlobs(scan.index, blobs)
        ContainerImageTar(
            index = processedIndex,
            images = resolvedManifests,
            manifest = scan.manifestJson,
            repositories = scan.repositories,
            layout = scan.ociLayout,
        )
    }

    /**
     * Two-phase parse: first reads metadata from [path] without spilling blobs, then performs a second pass
     * to spill required blobs to [tempDirectory], and returns a fully-resolved [ContainerImageTar].
     *
     * [path] - path to the docker image tar on disk (optionally gzip-compressed)
     * [isGzipped] - whether the tar archive is gzip-compressed
     * [tempDirectory] - directory where blob files are spilled during the second pass
     */
    suspend fun parse(path: Path, isGzipped: Boolean = false, tempDirectory: Path): ContainerImageTar {
        val blobEntryNames = mutableMapOf<Digest, String>()
        val scan = openTar(path, isGzipped).use { tar ->
            tar.scanEntries { digest, entry -> blobEntryNames[digest] = entry.name }
        }

        // second pass: spill only the blobs referenced by manifests
        val blobs = mutableMapOf<Digest, Path>()
        openTar(path, isGzipped).use { tar ->
            generateSequence(tar::getNextEntry).forEach { entry ->
                val digest = if (entry.name.startsWith("blobs/sha256/")) {
                    Digest.of("sha256:" + entry.name.removePrefix("blobs/sha256/"))
                } else {
                    tar.skip(entry.size)
                    return@forEach
                }
                if (digest in blobEntryNames) {
                    blobs[digest] = tar.processBlobEntry(entry, tempDirectory)
                } else {
                    tar.skip(entry.size)
                }
            }
        }

        val (processedIndex, resolvedManifests) = resolveManifestsAndBlobs(scan.index, blobs)
        return ContainerImageTar(
            index = processedIndex,
            images = resolvedManifests,
            manifest = scan.manifestJson,
            repositories = scan.repositories,
            layout = scan.ociLayout,
        )
    }

    /**
     * Parses metadata from an image tar at [path] without spilling blobs to disk.
     *
     * Performs a single sequential pass to collect the index and record which blob entry names exist,
     * then reopens [path] on demand to deserialize each manifest and config blob.
     *
     * [path] - path to the docker image tar on disk (optionally gzip-compressed)
     * [isGzipped] - whether the tar archive is gzip-compressed
     */
    suspend fun parse(path: Path, isGzipped: Boolean = false): ContainerImageMetadata {
        val blobEntryNames = mutableMapOf<Digest, String>()
        val scan = openTar(path, isGzipped).use { tar ->
            tar.scanEntries { digest, entry -> blobEntryNames[digest] = entry.name }
        }

        // reopens tar from path each time a blob needs to be read
        suspend fun blobReader(digest: Digest): InputStream? {
            val entryName = blobEntryNames[digest] ?: return null
            return withContext(Dispatchers.IO) { readBlobEntry(path, isGzipped, entryName) }
        }

        val (processedIndex, resolvedImages) = resolveManifestsMetadata(scan.index, ::blobReader)
        return ContainerImageMetadata(
            index = processedIndex,
            images = resolvedImages,
            manifest = scan.manifestJson,
            repositories = scan.repositories,
            layout = scan.ociLayout,
        )
    }

    // --- UPLOAD RESOLUTION ---

    private suspend fun resolveManifestsAndBlobs(
        index: ManifestList,
        blobPaths: Map<Digest, Path>,
    ): Pair<ManifestList, List<ContainerImageSingle>> {
        val attachments = mutableListOf<Digest>()
        val manifestBlobs = buildList {
            for (manifestEntry in index.manifests) {
                val entryDigest = manifestEntry.digest
                if (manifestEntry.platformIsUnknown()) {
                    attachments.add(entryDigest)
                    continue
                }
                val manifestBlobPath = blobPaths[entryDigest] ?: continue
                val manifestBlob =
                    BlobPath(entryDigest, manifestEntry.mediaType, manifestBlobPath, manifestEntry.size)
                addAll(resolveManifestsRecursively(entryDigest, blobPaths, manifestBlob))
            }
        }
        return index.withoutAttachments(attachments) to manifestBlobs
    }

    private suspend fun resolveManifestsRecursively(
        entryDigest: Digest,
        blobPaths: Map<Digest, Path>,
        manifestBlob: BlobPath,
    ) = buildList {
        when (val manifest = readManifest(entryDigest, diskBlobReader(blobPaths))) {
            null -> Unit

            is ManifestList -> {
                val (processedManifestList, processedManifests) = resolveManifestsAndBlobs(manifest, blobPaths)
                addAll(processedManifests)
                add(ContainerImageSingle(processedManifestList, entryDigest, listOf(manifestBlob)))
            }

            is ManifestSingle -> {
                val blobs = (manifest.layers + manifest.config).map { layer ->
                    val blobPath = blobPaths[layer.digest] ?: throw KircException.CorruptArchiveError(
                        "Could not resolve blob for layer or config with digest '${layer.digest}' during upload",
                    )
                    BlobPath(layer.digest, layer.mediaType, blobPath, layer.size)
                }
                add(ContainerImageSingle(manifest, entryDigest, blobs + manifestBlob))
            }
        }
    }

    // --- METADATA RESOLUTION ---

    private suspend fun resolveManifestsMetadata(
        index: ManifestList,
        blobReader: suspend (Digest) -> InputStream?,
    ): Pair<ManifestList, List<ContainerImageSingleMetadata>> {
        val attachments = mutableListOf<Digest>()
        val images = buildList {
            for (manifestEntry in index.manifests) {
                val entryDigest = manifestEntry.digest
                if (manifestEntry.platformIsUnknown()) {
                    attachments.add(entryDigest)
                    continue
                }
                addAll(resolveManifestsMetadataRecursively(entryDigest, blobReader))
            }
        }
        return index.withoutAttachments(attachments) to images
    }

    private suspend fun resolveManifestsMetadataRecursively(
        entryDigest: Digest,
        blobReader: suspend (Digest) -> InputStream?,
    ): List<ContainerImageSingleMetadata> = buildList {
        when (val manifest = readManifest(entryDigest, blobReader)) {
            null -> Unit

            is ManifestList -> {
                val (_, images) = resolveManifestsMetadata(manifest, blobReader)
                addAll(images)
            }

            is ManifestSingle -> {
                val config = readConfig(manifest, blobReader)
                add(ContainerImageSingleMetadata(manifest, entryDigest, manifest.layers, config))
            }
        }
    }

    // --- SHARED BLOB READING ---

    /**
     * Reads and deserialises a [Manifest] blob identified by [digest] using [blobReader].
     * Returns null if [blobReader] returns null for the given digest.
     */
    private suspend fun readManifest(digest: Digest, blobReader: suspend (Digest) -> InputStream?): Manifest? {
        val stream = blobReader(digest) ?: return null
        return runCatching {
            JsonMapper.deserialize<Manifest>(stream)
        }.getOrElse {
            throw KircException.CorruptArchiveError("Could not deserialize manifest (digest=$digest)", it)
        }
    }

    private suspend fun readConfig(
        manifest: ManifestSingle,
        blobReader: suspend (Digest) -> InputStream?,
    ): ImageConfig {
        val configDigest = manifest.config.digest
        val stream = blobReader(configDigest) ?: throw KircException.CorruptArchiveError(
            "Could not find config blob with digest '$configDigest'",
        )
        return runCatching {
            when (manifest.config.mediaType) {
                OciImageConfigV1.MediaType -> JsonMapper.deserialize<OciImageConfigV1>(stream)

                DockerImageConfigV1.MediaType -> JsonMapper.deserialize<DockerImageConfigV1>(stream)

                else -> throw KircException.CorruptArchiveError(
                    "Unknown config media type '${manifest.config.mediaType}'",
                )
            }
        }.getOrElse {
            if (it is KircException) throw it
            throw KircException.CorruptArchiveError("Could not deserialize config (digest=$configDigest)", it)
        }
    }

    /**
     * Returns a [blobReader][readManifest] lambda that opens blobs from already-spilled files in [blobPaths].
     */
    private fun diskBlobReader(blobPaths: Map<Digest, Path>): suspend (Digest) -> InputStream? = { digest ->
        blobPaths[digest]?.let { blobPath ->
            withContext(Dispatchers.IO) {
                SystemFileSystem.source(KotlinPath(blobPath.pathString)).buffered().asInputStream()
            }
        }
    }

    // --- TAR UTILITIES ---

    /**
     * Holds the structural (non-blob) entries collected during a [scanEntries] pass.
     */
    private class TarScanResult(
        val index: ManifestList,
        val ociLayout: OciLayout,
        val manifestJson: ManifestJson?,
        val repositories: Repositories?,
    )

    /**
     * Scans all entries in this [TarArchiveInputStream], dispatching structural entries into a [TarScanResult]
     * and delegating every blob entry (`blobs/sha256/…`) to [onBlob].
     *
     * Directories and unrecognised entries are skipped automatically.
     * Throws [KircException.CorruptArchiveError] if `index.json` or `oci-layout` are missing.
     */
    private suspend fun TarArchiveInputStream.scanEntries(
        onBlob: suspend (digest: Digest, entry: TarArchiveEntry) -> Unit,
    ): TarScanResult {
        var indexFile: ManifestList? = null
        var repositoriesFile: Repositories? = null
        var manifestJsonFile: ManifestJson? = null
        var ociLayoutFile: OciLayout? = null

        generateSequence(::getNextEntry).forEach { entry ->
            when {
                entry.isDirectory -> skip(entry.size)

                entry.name.startsWith("blobs/sha256/") -> {
                    val digest = Digest.of("sha256:" + entry.name.removePrefix("blobs/sha256/"))
                    onBlob(digest, entry)
                }

                "index.json" == entry.name -> indexFile = deserializeEntry<ManifestList>(entry)

                "repositories" == entry.name -> repositoriesFile = deserializeEntry<Repositories>(entry)

                "manifest.json" == entry.name -> manifestJsonFile = deserializeEntry<ManifestJson>(entry)

                "oci-layout" == entry.name -> ociLayoutFile = deserializeEntry<OciLayout>(entry)

                else -> skip(entry.size)
            }
        }

        val index = indexFile ?: throw KircException.CorruptArchiveError(
            "index should be present inside provided docker image",
        )
        val ociLayout = ociLayoutFile ?: throw KircException.CorruptArchiveError(
            "'oci-layout' file should be present inside the provided docker image",
        )
        return TarScanResult(index, ociLayout, manifestJsonFile, repositoriesFile)
    }

    /**
     * Returns a [ManifestList] with entries whose digest is in [attachments] removed,
     * preserving the concrete subtype.
     */
    private fun ManifestList.withoutAttachments(attachments: Collection<Digest>): ManifestList {
        val filtered = manifests.filterNot { it.digest in attachments }
        return when (this) {
            is DockerManifestListV1 -> copy(manifests = filtered)
            is OciManifestListV1 -> copy(manifests = filtered)
        }
    }

    private fun openTar(path: Path, isGzipped: Boolean): TarArchiveInputStream {
        val raw = path.inputStream()
        val unwrapped = if (isGzipped) GZIPInputStream(raw) else raw
        return TarArchiveInputStream(unwrapped)
    }

    /**
     * Reopens the tar at [path] and scans forward to [entryName], returning its content as an [InputStream].
     * The returned stream must be closed by the caller.
     */
    private fun readBlobEntry(path: Path, isGzipped: Boolean, entryName: String): InputStream {
        val tar = openTar(path, isGzipped)
        generateSequence(tar::getNextEntry).forEach { entry ->
            if (entry.name == entryName) {
                // wrap tar so closing the returned stream also closes the tar
                return object : InputStream() {
                    override fun read(): Int = tar.read()
                    override fun read(b: ByteArray, off: Int, len: Int): Int = tar.read(b, off, len)
                    override fun close() = tar.close()
                }
            }
            tar.skip(entry.size)
        }
        tar.close()
        throw KircException.CorruptArchiveError("Blob entry '$entryName' not found in tar")
    }
}
