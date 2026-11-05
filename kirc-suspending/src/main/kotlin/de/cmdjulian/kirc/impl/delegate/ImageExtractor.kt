package de.cmdjulian.kirc.impl.delegate

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.ManifestJsonEntry
import de.cmdjulian.kirc.spec.Platform
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.image.DockerImageConfigV1
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.image.OciImageConfigV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import kotlinx.coroutines.runBlocking
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.inputStream

/**
 * Helper class to extract certain parts of a docker image, e.g. Index, Manifest, Config
 *
 * @param path location of docker image
 * @param isGzipped optionally, to signalize tar archive is gzipped, and it needs to be unwrapped during extraction
 */
class ImageExtractor(private val path: Path, private val isGzipped: Boolean = false) {

    private val source: Source
        get() = path.inputStream().asSource().buffered()
    private val blobPath: String = "blobs/sha256/"
    private val indexMediaTypes = setOf(OciManifestListV1.MediaType, DockerManifestListV1.MediaType)

    /** Reads the archive from the start; every call is a full (gunzip +) scan up to the entries [block] consumes */
    private inline fun <T> scan(block: (TarArchiveInputStream, Sequence<TarArchiveEntry>) -> T): T =
        source.use { source ->
            val unwrapStream = if (isGzipped) GZIPInputStream(source.asInputStream()) else source.asInputStream()

            unwrapStream.use { unwrapStream ->
                TarArchiveInputStream(unwrapStream).use { stream ->
                    block(stream, generateSequence(stream::getNextEntry).filterNot(TarArchiveEntry::isDirectory))
                }
            }
        }

    private inline fun <T : Any> withEntry(path: String, block: (TarArchiveInputStream, TarArchiveEntry) -> T): T? =
        scan { stream, entries -> entries.firstOrNull { entry -> entry.name == path }?.let { block(stream, it) } }

    private inline fun <reified T : Any> extract(path: String): T? =
        withEntry(path) { stream, entry -> runBlocking { stream.deserializeEntry<T>(entry) } }

    private fun read(path: String): ByteArray? = withEntry(path) { stream, _ -> stream.readAllBytes() }

    // --- TOP LEVEL MODELS ---

    fun index(): ManifestList? = extract(INDEX_JSON)

    fun repositoriesJson(): Repositories? = extract("repositories")

    fun manifestJson(): ManifestJson? = extract(MANIFEST_JSON)

    // --- BLOB LEVEL MODELS ---

    /** Get a nested [ManifestList] (image index) by [Digest] */
    fun index(digest: Digest): ManifestList? = blobAsType(digest)

    /**
     * Get the manifest for [platform] by extracting from [ManifestList]
     *
     * Follows nested image indexes (e.g. written by the containerd image store) and skips manifests with an unknown
     * platform (attestations, cache, etc.). Manifests without a platform, as written by a single-arch `docker save`,
     * match any [platform].
     *
     * If no manifest matches [platform], throws [KircException.PlatformNotMatching]
     */
    fun manifest(index: ManifestList, platform: Platform = Platform.current()): ManifestSingle? =
        findManifest(index, platform, ::index, ::manifest) ?: throw KircException.PlatformNotMatching(platform)

    private fun findManifest(
        index: ManifestList,
        platform: Platform,
        nestedIndex: (Digest) -> ManifestList?,
        manifest: (Digest) -> ManifestSingle?,
    ): ManifestSingle? = index.manifests
        .asSequence()
        .filterNot(ManifestListEntry::platformIsUnknown)
        .firstNotNullOfOrNull { entry ->
            when {
                entry.mediaType in indexMediaTypes ->
                    nestedIndex(entry.digest)?.let { findManifest(it, platform, nestedIndex, manifest) }

                entry.matches(platform) -> manifest(entry.digest)
                else -> null
            }
        }

    // manifests without a platform (single-arch `docker save`) match any platform
    private fun ManifestListEntry.matches(platform: Platform): Boolean =
        this.platform?.let { Platform(it.os, it.architecture) == platform } ?: true

    /** Get manifest by [Digest] */
    fun manifest(digest: Digest): ManifestSingle? = blobAsType(digest)

    /**
     *  Get config by extracting from [ManifestSingle]
     *
     *  Deserializes based on config media type defined in manifest
     */
    fun config(manifest: ManifestSingle): ImageConfig? {
        val name = blobPath + manifest.config.digest.hash
        return read(name)?.let { bytes -> parse(name, bytes, configType(manifest)) }
    }

    private fun configType(manifest: ManifestSingle): Class<out ImageConfig> = when (manifest.config.mediaType) {
        OciImageConfigV1.MediaType -> OciImageConfigV1::class.java
        DockerImageConfigV1.MediaType -> DockerImageConfigV1::class.java
        else -> error("Unknown config media type encountered in manifest: ${manifest.config.mediaType}")
    }

    /**
     * Get config by [Digest]
     *
     * Since both Docker and OCI config have the same structure, we try to parse as OCI first,
     */
    fun config(digest: Digest): ImageConfig? = try {
        blobAsType<OciImageConfigV1>(digest)
    } catch (_: InvalidDefinitionException) {
        blobAsType<DockerImageConfigV1>(digest)
    }

    /** Be careful, as this loads the content of a potentially large blob into memory */
    fun blob(digest: Digest): ByteArray? = read(blobPath + digest.hash)

    /** Retrieve Blob with certain [Digest] as type [T] */
    private inline fun <reified T : Any> blobAsType(digest: Digest): T? = extract(blobPath + digest.hash)

    // --- SINGLE PASS ---

    /**
     * Resolve index, the manifest for [platform], its config and `manifest.json` in a single read of the archive.
     *
     * The other methods each read the archive from the start, which for a gzipped multi-GB image means one full
     * decompression per call. The OCI image layout doesn't fix the order of tar entries (blobs may come before the
     * `index.json` that references them), so this keeps `index.json`, `manifest.json` and every JSON blob of at most
     * [MAX_METADATA_BLOB_SIZE] bytes seen during the pass, then resolves from those. Layers are skipped.
     *
     * Follows the same rules as [manifest] (nested indexes, attestations skipped, single-arch manifests match any
     * [platform]) and throws [KircException.PlatformNotMatching] if the archive has an index but no manifest for
     * [platform].
     */
    fun metadata(platform: Platform = Platform.current()): ImageArchiveMetadata {
        val entries = readMetadataEntries()
        val index = entries.parse(INDEX_JSON, ManifestList::class.java)
        val manifest = index?.let {
            val nestedIndex = { digest: Digest -> entries.parse(blobPath + digest.hash, ManifestList::class.java) }
            val manifest = { digest: Digest -> entries.parse(blobPath + digest.hash, ManifestSingle::class.java) }
            findManifest(it, platform, nestedIndex, manifest) ?: throw KircException.PlatformNotMatching(platform)
        }
        val config = manifest?.let { entries.parse(blobPath + it.config.digest.hash, configType(it)) }
        val manifestJson = entries.parse(MANIFEST_JSON, Array<ManifestJsonEntry>::class.java)

        return ImageArchiveMetadata(index, manifest, config, manifestJson?.toList())
    }

    private fun readMetadataEntries(): Map<String, ByteArray> = scan { stream, entries ->
        entries
            .filter { entry -> entry.name in TOP_LEVEL_FILES || entry.isMetadataBlobCandidate() }
            .map { entry -> entry.name to stream.readNBytes(entry.size.toInt()) }
            .filter { (name, bytes) -> name in TOP_LEVEL_FILES || bytes.firstOrNull() == JSON_OBJECT_START }
            .toMap()
    }

    private fun TarArchiveEntry.isMetadataBlobCandidate() = name.startsWith(blobPath) && size <= MAX_METADATA_BLOB_SIZE

    private fun <T> Map<String, ByteArray>.parse(name: String, type: Class<T>): T? = get(name)?.let { bytes ->
        parse(name, bytes, type)
    }

    private fun <T> parse(name: String, bytes: ByteArray, type: Class<T>): T =
        runCatching { JsonMapper.readValue(bytes, type) }
            .getOrElse { throw IllegalStateException("Failed to deserialize tar entry '$name'", it) }

    companion object {
        /** Blobs larger than this are never manifests, indexes or configs and are skipped by [metadata] */
        const val MAX_METADATA_BLOB_SIZE: Long = 1024 * 1024
        private const val INDEX_JSON = "index.json"
        private const val MANIFEST_JSON = "manifest.json"
        private val TOP_LEVEL_FILES = setOf(INDEX_JSON, MANIFEST_JSON)
        private const val JSON_OBJECT_START = '{'.code.toByte()
    }
}
