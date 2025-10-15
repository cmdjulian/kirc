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
import de.cmdjulian.kirc.utils.toByteReadChannel
import de.cmdjulian.kirc.utils.toKotlinPath
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

internal class ImageUploader(private val client: SuspendingContainerImageRegistryClient, private val tmpPath: Path) {

    @OptIn(ExperimentalPathApi::class, ExperimentalCoroutinesApi::class)
    suspend fun upload(repository: Repository, reference: Reference, tar: Source, mode: BlobUploadMode): Digest =
        coroutineScope {
            // store data temporarily
            val tempDirectory = Path.of(
                tmpPath.pathString,
                "${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)}--$repository--$reference",
            )
            withContext(Dispatchers.IO) {
                SystemFileSystem.createDirectories(tempDirectory.toKotlinPath())
            }

            // read from temp file and deserialize
            val uploadContainerImage = readFromTar(tar, tempDirectory)

            // upload max three layers in parallel at most
            val blobUploadDispatcher = Dispatchers.Default.limitedParallelism(3)
            // upload architecture images
            try {
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
            } finally {
                // clear up temporary blob files
                cleanupTempDirectory(tempDirectory)
            }

            // upload index
            client.uploadManifest(repository, reference, uploadContainerImage.index)
        }

    private suspend fun uploadBlob(repository: Repository, blob: UploadBlobPath, mode: BlobUploadMode) {
        if (!client.existsBlob(repository, blob.digest)) {
            val session = client.initiateBlobUpload(repository)

            val createSource: suspend () -> Source = {
                withContext(Dispatchers.IO) {
                    SystemFileSystem.source(blob.path.toKotlinPath()).buffered()
                }
            }

            when (mode) {
                BlobUploadMode.Stream -> client.uploadBlobStream(
                    session,
                    blob.digest,
                    { createSource().toByteReadChannel() },
                    blob.size,
                )

                is BlobUploadMode.Chunks -> {
                    val endSession = client.uploadBlobChunks(session, createSource(), mode.chunkSize)
                    // chunked upload requires a final request
                    client.finishBlobUpload(endSession, blob.digest)
                }
            }
        }
    }

    private fun handleError(e: Exception) {
        logger.error(e) { "Error uploading image to registry" }
        when (e) {
            is KircException, is RegistryException -> throw e
            else -> throw KircException.UnexpectedError(
                "Unexpected error, could not upload image to registry: ${e.cause}",
                e,
            )
        }
    }

    private suspend fun cleanupTempDirectory(tempDirectory: Path) {
        withContext(Dispatchers.IO) {
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
                    entry.isDirectory -> stream.readEntry(entry)

                    "blobs/sha256/" in entry.name -> blobs[entry.name] = stream.processBlobEntry(entry, tempDirectory)

                    "index.json" in entry.name -> indexFile = stream.deserializeEntry<ManifestList>(entry)

                    "repositories" in entry.name -> repositoriesFile = stream.deserializeEntry<Repositories>(entry)

                    "manifest.json" in entry.name -> manifestJsonFile = stream.deserializeEntry<ManifestJson>(entry)

                    "oci-layout" in entry.name -> ociLayoutFile = stream.deserializeEntry<OciLayout>(entry)

                    else -> stream.readEntry(entry)
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

    private suspend fun TarArchiveInputStream.readEntry(entry: TarArchiveEntry): ByteArray =
        withContext(Dispatchers.IO) { readNBytes(entry.size.toInt()) }

    // We deserialize entries which aren't blobs. They are small enough to be loaded to memory
    private suspend inline fun <reified T : Any> TarArchiveInputStream.deserializeEntry(entry: TarArchiveEntry): T =
        jacksonDeserializer<T>().deserialize(readEntry(entry))

    private suspend fun TarArchiveInputStream.processBlobEntry(entry: TarArchiveEntry, tempDirectory: Path): Path {
        val blobDigest = entry.name.removePrefix("blobs/sha256/")
        val tempPath = Path.of(tempDirectory.pathString, blobDigest)
        withContext(Dispatchers.IO) {
            SystemFileSystem.sink(tempPath.toKotlinPath()).buffered().also { path ->
                path.write(this@processBlobEntry.asSource(), entry.size)
                path.flush()
            }
        }
        return tempPath
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
