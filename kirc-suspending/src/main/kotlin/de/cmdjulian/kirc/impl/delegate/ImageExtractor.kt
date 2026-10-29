package de.cmdjulian.kirc.impl.delegate

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.ManifestJson
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

    private inline fun <T : Any> withEntry(path: String, block: (TarArchiveInputStream, TarArchiveEntry) -> T): T? =
        source.use { source ->
            val unwrapStream = if (isGzipped) GZIPInputStream(source.asInputStream()) else source.asInputStream()

            unwrapStream.use { unwrapStream ->
                TarArchiveInputStream(unwrapStream).use { stream ->
                    generateSequence(stream::getNextEntry)
                        .filterNot(TarArchiveEntry::isDirectory)
                        .firstOrNull { entry -> entry.name == path }
                        ?.let { entry -> block(stream, entry) }
                }
            }
        }

    private inline fun <reified T : Any> extract(path: String): T? =
        withEntry(path) { stream, entry -> runBlocking { stream.deserializeEntry<T>(entry) } }

    private fun read(path: String): ByteArray? = withEntry(path) { stream, _ -> stream.readAllBytes() }

    // --- TOP LEVEL MODELS ---

    fun index(): ManifestList? = extract("index.json")

    fun repositoriesJson(): Repositories? = extract("repositories")

    fun manifestJson(): ManifestJson? = extract("manifest.json")

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
        findManifest(index, platform) ?: throw KircException.PlatformNotMatching(platform)

    private fun findManifest(index: ManifestList, platform: Platform): ManifestSingle? = index.manifests
        .asSequence()
        .filterNot(ManifestListEntry::platformIsUnknown)
        .firstNotNullOfOrNull { entry ->
            when {
                entry.mediaType in indexMediaTypes -> index(entry.digest)?.let { findManifest(it, platform) }
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
    fun config(manifest: ManifestSingle): ImageConfig? = when (manifest.config.mediaType) {
        OciImageConfigV1.MediaType -> extract<OciImageConfigV1>(blobPath + manifest.config.digest.hash)
        DockerImageConfigV1.MediaType -> extract<DockerImageConfigV1>(blobPath + manifest.config.digest.hash)
        else -> error("Unknown manifest single type encountered in manifest: ${manifest.mediaType}")
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
}
