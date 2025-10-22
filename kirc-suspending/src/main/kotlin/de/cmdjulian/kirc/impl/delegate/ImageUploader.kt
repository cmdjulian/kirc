package de.cmdjulian.kirc.impl.delegate

import de.cmdjulian.kirc.client.BlobUploadMode
import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.exception.RegistryException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.serialization.jacksonDeserializer
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.UploadBlobPath
import de.cmdjulian.kirc.spec.UploadContainerImage
import de.cmdjulian.kirc.spec.UploadSingleImage
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.utils.toKotlinPath
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

internal class ImageUploader(private val client: SuspendingContainerImageRegistryClient, private val tmpPath: Path) {

    @OptIn(ExperimentalPathApi::class, ExperimentalCoroutinesApi::class)
    suspend fun upload(repository: Repository, reference: Reference, tar: Source, mode: BlobUploadMode): Digest =
        coroutineScope {
            // store data temporarily (sanitize name for cross-platform safety, replace ':' which is invalid on Windows)
            val timePart =
                OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val safeRef = reference.toString().replace(':', '_')
            val tempDirectory = Path.of(
                tmpPath.pathString,
                "${timePart}--$repository--$safeRef",
            )
            withContext(Dispatchers.IO) {
                SystemFileSystem.createDirectories(tempDirectory.toKotlinPath())
            }

            try {
                // read from tar and deserialize (can throw; ensure cleanup in finally)
                val uploadContainerImage = readFromTar(tar, tempDirectory)

                // upload max three blobs in parallel at most (mimic docker)
                val blobUploadDispatcher = Dispatchers.Default.limitedParallelism(3)

                // upload architecture images
                for ((manifest, manifestDigest, blobs) in uploadContainerImage.images) {
                    // upload blobs in parallel, but only up to three at once to mimic how Docker does it
                    coroutineScope {
                        withContext(blobUploadDispatcher) {
                            for (blob in blobs.distinctBy(UploadBlobPath::digest)) {
                                launch {
                                    uploadBlob(repository, blob, mode)
                                }
                            }
                        }
                    }
                    client.uploadManifest(repository, manifestDigest, manifest)
                }

                // upload index (manifest list) last
                client.uploadManifest(repository, reference, uploadContainerImage.index)
            } catch (e: RuntimeException) {
                throw handleError(e)
            } finally {
                // clear up temporary blob files
                runCatching { cleanupTempDirectory(tempDirectory) }
                    .onFailure { t ->
                        logger.info(t) { "Failed to fully cleanup temp directory: ${tempDirectory.pathString}" }
                    }
            }
        }

    private suspend fun uploadBlob(repository: Repository, blob: UploadBlobPath, mode: BlobUploadMode) {
        if (!client.existsBlob(repository, blob.digest)) {
            val session = client.initiateBlobUpload(repository)

            when (mode) {
                BlobUploadMode.Stream -> client.uploadBlobStream(session, blob.digest, blob.path, blob.size)

                is BlobUploadMode.Chunks -> {
                    val endSession = client.uploadBlobChunks(session, blob.path, mode.chunkSize)
                    // chunked upload requires a final request
                    client.finishBlobUpload(endSession, blob.digest)
                }
            }
        }
    }

    private fun handleError(e: Exception): RuntimeException = when (e) {
        is KircException, is RegistryException -> e
        else -> KircException.UnexpectedError(
            "Unexpected error, could not upload image to registry: ${e.cause}",
            e,
        )
    }.also { kircException ->
        logger.error(kircException) { "Error uploading image to registry" }
    }

    private suspend fun cleanupTempDirectory(tempDirectory: Path) {
        withContext(Dispatchers.IO) {
            // current implementation stores blobs flatly; still, iterate and delete defensively
            SystemFileSystem.list(tempDirectory.toKotlinPath()).forEach(SystemFileSystem::delete)
            SystemFileSystem.delete(tempDirectory.toKotlinPath())
        }
    }

    // PARSE INPUT TAR

    private suspend fun readFromTar(tarSource: Source, tempDirectory: Path) = tarSource.use { source ->
        TarArchiveInputStream(source.asInputStream()).use { stream ->
            val blobs = mutableMapOf<String, Path>()
            var indexFile: ManifestList? = null
            var repositoriesFile: Repositories? = null
            var manifestJsonFile: ManifestJson? = null
            var ociLayoutFile: OciLayout? = null

            generateSequence(stream::getNextEntry).forEach { entry ->
                when {
                    entry.isDirectory -> stream.skip(entry.size)

                    entry.name.startsWith("blobs/sha256/") -> blobs[entry.name] =
                        stream.processBlobEntry(entry, tempDirectory)

                    "index.json" == entry.name -> indexFile = stream.deserializeEntry<ManifestList>(entry)

                    "repositories" == entry.name -> repositoriesFile = stream.deserializeEntry<Repositories>(entry)

                    "manifest.json" == entry.name -> manifestJsonFile = stream.deserializeEntry<ManifestJson>(entry)

                    "oci-layout" == entry.name -> ociLayoutFile = stream.deserializeEntry<OciLayout>(entry)

                    else -> stream.skip(entry.size)
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

            UploadContainerImage(
                index = indexFile,
                images = associateManifestsWithBlobs(indexFile, blobs),
                manifest = manifestJsonFile,
                repositories = repositoriesFile,
                layout = ociLayoutFile,
            )
        }
    }

    private fun resolveBlobPath(blobs: Map<String, Path>, namePart: String): Path =
        blobs.keys.first { blobName -> namePart in blobName }.let(blobs::get)
            ?: throw KircException.CorruptArchiveError(
                "Could not find blob file containing '$namePart' in deserialized list",
            )

    private suspend fun resolveManifest(blobs: Map<String, Path>, manifestEntry: ManifestListEntry): ManifestSingle {
        val blobPath = resolveBlobPath(blobs, "blobs/sha256/${manifestEntry.digest.hash}")

        val manifestStream = withContext(Dispatchers.IO) {
            SystemFileSystem.source(blobPath.toKotlinPath()).buffered().asInputStream()
        }
        return jacksonDeserializer<ManifestSingle>().deserialize(manifestStream)
    }

    /**
     * Associates manifest with their layer blobs and config blobs
     */
    private suspend fun associateManifestsWithBlobs(
        index: ManifestList,
        blobs: Map<String, Path>,
    ): List<UploadSingleImage> = index.manifests.map { manifestEntry ->
        // resolve manifests from index
        val manifest = resolveManifest(blobs, manifestEntry)
        val manifestDigest = manifestEntry.digest

        // associate manifests to their blobs and config
        val layerBlobs = manifest.layers.map { layer ->
            val blobPath = resolveBlobPath(blobs, layer.digest.hash)
            UploadBlobPath(layer.digest, layer.mediaType, blobPath, layer.size)
        }
        val configBlob = manifest.config.let { config ->
            val blobPath = resolveBlobPath(blobs, config.digest.hash)
            // technically no layer blob but handled as blob when uploaded
            UploadBlobPath(config.digest, config.mediaType, blobPath, config.size)
        }
        val manifestBlob = manifestBlobPath(blobs, manifestDigest, manifest)

        UploadSingleImage(manifest, manifestDigest, layerBlobs + configBlob + manifestBlob)
    }

    private suspend fun manifestBlobPath(
        blobs: Map<String, Path>,
        manifestDigest: Digest,
        manifest: Manifest,
    ): UploadBlobPath {
        val manifestPath = resolveBlobPath(blobs, manifestDigest.hash)
        val size = withContext(Dispatchers.IO) {
            SystemFileSystem.metadataOrNull(manifestPath.toKotlinPath())?.size
        } ?: throw KircException.CorruptArchiveError(
            "Could not determine file metadata for manifest '$manifestDigest'",
        )
        val mediaType = manifest.mediaType
            ?: throw KircException.CorruptArchiveError("Could not determine media type of manifest $manifestDigest")
        return UploadBlobPath(manifestDigest, mediaType, manifestPath, size)
    }
}
