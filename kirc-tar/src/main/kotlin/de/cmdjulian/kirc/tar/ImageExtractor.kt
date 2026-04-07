@file:OptIn(InternalKircApi::class)

package de.cmdjulian.kirc.tar

import de.cmdjulian.kirc.annotation.InternalKircApi
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
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
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

    /**
     * Performs a single sequential pass through [source], spilling blobs to [tempDirectory], then resolves and
     * returns a fully-parsed [ContainerImageTar].
     *
     * [source] - the tar source to read from (consumed exactly once)
     * [tempDirectory] - directory where blob files are spilled during parsing
     */
    suspend fun parse(source: Source, tempDirectory: Path): ContainerImageTar = source.use {
        parse(it.asInputStream(), tempDirectory)
    }

    /**
     * Performs a single sequential pass through [input], spilling blobs to [tempDirectory], then resolves and
     * returns a fully-parsed [ContainerImageTar].
     *
     * [input] - the tar input stream to read from (consumed exactly once)
     * [tempDirectory] - directory where blob files are spilled during parsing
     */
    suspend fun parse(input: InputStream, tempDirectory: Path): ContainerImageTar = input.use { stream ->
        TarArchiveInputStream(stream).use { tar ->
            val blobs = mutableMapOf<Digest, Path>()
            var indexFile: ManifestList? = null
            var repositoriesFile: Repositories? = null
            var manifestJsonFile: ManifestJson? = null
            var ociLayoutFile: OciLayout? = null

            generateSequence(tar::getNextEntry).forEach { entry ->
                when {
                    entry.isDirectory -> tar.skip(entry.size)

                    entry.name.startsWith("blobs/sha256/") -> {
                        val digest = Digest.of("sha256:" + entry.name.removePrefix("blobs/sha256/"))
                        blobs[digest] = tar.processBlobEntry(entry, tempDirectory)
                    }

                    "index.json" == entry.name -> indexFile = tar.deserializeEntry<ManifestList>(entry)

                    "repositories" == entry.name ->
                        repositoriesFile = tar.deserializeEntry<Repositories>(entry)

                    "manifest.json" == entry.name ->
                        manifestJsonFile = tar.deserializeEntry<ManifestJson>(entry)

                    "oci-layout" == entry.name -> ociLayoutFile = tar.deserializeEntry<OciLayout>(entry)

                    else -> tar.skip(entry.size)
                }
            }

            when {
                indexFile == null -> throw KircException.CorruptArchiveError(
                    "index should be present inside provided docker image",
                )

                ociLayoutFile == null -> throw KircException.CorruptArchiveError(
                    "'oci-layout' file should be present inside the provided docker image",
                )
            }

            val (processedIndex, resolvedManifests) = resolveManifestsAndBlobs(indexFile, blobs)
            ContainerImageTar(
                index = processedIndex,
                images = resolvedManifests,
                manifest = manifestJsonFile,
                repositories = repositoriesFile,
                layout = ociLayoutFile,
            )
        }
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
        // single pass: collect structural entries and record blob entry names (no blob data read)
        val blobEntryNames = mutableMapOf<Digest, String>()
        var indexFile: ManifestList? = null
        var repositoriesFile: Repositories? = null
        var manifestJsonFile: ManifestJson? = null
        var ociLayoutFile: OciLayout? = null

        openTar(path, isGzipped).use { tar ->
            generateSequence(tar::getNextEntry).forEach { entry ->
                when {
                    entry.isDirectory -> tar.skip(entry.size)

                    entry.name.startsWith("blobs/sha256/") -> {
                        val digest = Digest.of("sha256:" + entry.name.removePrefix("blobs/sha256/"))
                        blobEntryNames[digest] = entry.name
                    }

                    "index.json" == entry.name -> indexFile = tar.deserializeEntry<ManifestList>(entry)

                    "repositories" == entry.name ->
                        repositoriesFile = tar.deserializeEntry<Repositories>(entry)

                    "manifest.json" == entry.name ->
                        manifestJsonFile = tar.deserializeEntry<ManifestJson>(entry)

                    "oci-layout" == entry.name -> ociLayoutFile = tar.deserializeEntry<OciLayout>(entry)

                    else -> tar.skip(entry.size)
                }
            }
        }

        when {
            indexFile == null -> throw KircException.CorruptArchiveError(
                "index should be present inside provided docker image",
            )

            ociLayoutFile == null -> throw KircException.CorruptArchiveError(
                "'oci-layout' file should be present inside the provided docker image",
            )
        }

        // reopens tar from path each time a blob needs to be read
        suspend fun blobReader(digest: Digest): InputStream? {
            val entryName = blobEntryNames[digest] ?: return null
            return withContext(Dispatchers.IO) { readBlobEntry(path, isGzipped, entryName) }
        }

        val (processedIndex, resolvedImages) = resolveManifestsMetadata(indexFile, ::blobReader)
        return ContainerImageMetadata(
            index = processedIndex,
            images = resolvedImages,
            manifest = manifestJsonFile,
            repositories = repositoriesFile,
            layout = ociLayoutFile,
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
        val manifests = index.manifests.filterNot { it.digest in attachments }
        val resolvedIndex = when (index) {
            is DockerManifestListV1 -> index.copy(manifests = manifests)
            is OciManifestListV1 -> index.copy(manifests = manifests)
        }
        return resolvedIndex to manifestBlobs
    }

    private suspend fun resolveManifestsRecursively(
        entryDigest: Digest,
        blobPaths: Map<Digest, Path>,
        manifestBlob: BlobPath,
    ) = buildList {
        when (val manifest = readManifestFromDisk(blobPaths, entryDigest)) {
            null -> Unit

            is ManifestList -> {
                val (processedManifestList, processedManifests) = resolveManifestsAndBlobs(manifest, blobPaths)
                addAll(processedManifests)
                add(ContainerImageSingle(processedManifestList, entryDigest, listOf(manifestBlob)))
            }

            is ManifestSingle -> {
                val blobs = (manifest.layers + manifest.config).map { layer ->
                    val blobPath = blobPaths[layer.digest] ?: throw KircException.CorruptArchiveError(
                        "Could not resolve blob for layer or " +
                            "config with digest '${layer.digest}' during upload",
                    )
                    BlobPath(layer.digest, layer.mediaType, blobPath, layer.size)
                }
                add(ContainerImageSingle(manifest, entryDigest, blobs + manifestBlob))
            }
        }
    }

    private suspend fun readManifestFromDisk(blobs: Map<Digest, Path>, manifestDigest: Digest): Manifest? {
        val blobPath = blobs[manifestDigest] ?: return null
        val stream = withContext(Dispatchers.IO) {
            SystemFileSystem.source(KotlinPath(blobPath.pathString)).buffered().asInputStream()
        }
        return runCatching {
            JsonMapper.deserialize<Manifest>(stream)
        }.getOrElse {
            throw KircException.CorruptArchiveError("Could not deserialize manifest (digest=$manifestDigest)", it)
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
        val manifests = index.manifests.filterNot { it.digest in attachments }
        val resolvedIndex = when (index) {
            is DockerManifestListV1 -> index.copy(manifests = manifests)
            is OciManifestListV1 -> index.copy(manifests = manifests)
        }
        return resolvedIndex to images
    }

    private suspend fun resolveManifestsMetadataRecursively(
        entryDigest: Digest,
        blobReader: suspend (Digest) -> InputStream?,
    ): List<ContainerImageSingleMetadata> = buildList {
        when (val manifest = readManifestFromTar(entryDigest, blobReader)) {
            null -> Unit

            is ManifestList -> {
                val (_, images) = resolveManifestsMetadata(manifest, blobReader)
                addAll(images)
            }

            is ManifestSingle -> {
                val config = readConfigFromTar(manifest, blobReader)
                add(ContainerImageSingleMetadata(manifest, entryDigest, manifest.layers, config))
            }
        }
    }

    private suspend fun readManifestFromTar(
        manifestDigest: Digest,
        blobReader: suspend (Digest) -> InputStream?,
    ): Manifest? {
        val stream = blobReader(manifestDigest) ?: return null
        return runCatching {
            JsonMapper.deserialize<Manifest>(stream)
        }.getOrElse {
            throw KircException.CorruptArchiveError("Could not deserialize manifest (digest=$manifestDigest)", it)
        }
    }

    private suspend fun readConfigFromTar(
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

    // --- TAR UTILITIES ---

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
