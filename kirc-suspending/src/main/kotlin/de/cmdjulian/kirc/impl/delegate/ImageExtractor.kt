package de.cmdjulian.kirc.impl.delegate

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.image.DockerImageConfigV1
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.image.OciImageConfigV1
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
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

    private inline fun <reified T : Any> extract(path: String): T? = source.use { source ->
        val unwrapStream = if (isGzipped) GZIPInputStream(source.asInputStream()) else source.asInputStream()

        unwrapStream.use { unwrapStream ->
            TarArchiveInputStream(unwrapStream).use { stream ->
                generateSequence(stream::getNextEntry)
                    .filterNot(TarArchiveEntry::isDirectory)
                    .firstOrNull { entry -> entry.name == path }
                    ?.let { entry -> runBlocking { stream.deserializeEntry(entry) } }
            }
        }
    }

    // --- TOP LEVEL MODELS ---

    fun index(): ManifestList? = extract("index.json")

    fun repositoriesJson(): Repositories? = extract("repositories")

    fun manifestJson(): ManifestJson? = extract("manifest.json")

    // --- BLOB LEVEL MODELS ---

    /** Get manifest by extracting from [ManifestList] */
    fun manifest(index: ManifestList): ManifestSingle? =
        index.manifests.firstOrNull()?.digest?.let { digest -> extract(blobPath + digest.hash) }

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
    fun blob(digest: Digest): ByteArray? = extract(blobPath + digest.hash)

    /** Retrieve Blob with certain [Digest] as type [T] */
    private inline fun <reified T : Any> blobAsType(digest: Digest): T? = extract(blobPath + digest.hash)
}
