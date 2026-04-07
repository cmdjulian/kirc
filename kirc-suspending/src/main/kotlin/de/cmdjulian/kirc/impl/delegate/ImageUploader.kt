@file:OptIn(InternalKircApi::class)

package de.cmdjulian.kirc.impl.delegate

import de.cmdjulian.kirc.annotation.InternalKircApi
import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.client.UploadMode
import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.exception.RegistryException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.auth.ScopeType
import de.cmdjulian.kirc.impl.auth.withAuthSession
import de.cmdjulian.kirc.tar.BlobPath
import de.cmdjulian.kirc.tar.ImageExtractor
import de.cmdjulian.kirc.utils.createSafePath
import de.cmdjulian.kirc.utils.toKotlinPath
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.files.SystemFileSystem
import java.nio.file.Path
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

internal class ImageUploader(private val client: SuspendingContainerImageRegistryClient, private val tmpPath: Path) {

    /**
     * Uploads the provided tar source as an image to the specified repository and reference.
     *
     * Documentation for the upload flow can be found in the `docs/upload-graph.md` file at project root.
     *
     * [repository] - Repository to upload the image to
     * [reference] - Reference (tag or digest) to upload the image as
     * [tar] - Source tar containing the image data
     * [mode] - Upload mode to use for blob uploads (Stream or Chunks)
     */
    suspend fun upload(repository: Repository, reference: Reference, tar: Source, mode: UploadMode): Digest =
        withAuthSession {
            // store data temporarily (sanitize name for cross-platform safety)
            val tempDirectory = createSafePath(tmpPath, repository, reference)
            withContext(Dispatchers.IO) {
                SystemFileSystem.createDirectories(tempDirectory.toKotlinPath())
            }

            try {
                // read from tar and deserialize (can throw; ensure cleanup in finally)
                val uploadContainerImage = ImageExtractor.parse(tar, tempDirectory)

                // initialize auth for upload flow
                client.initializeAuth(repository, ScopeType.PULL_PUSH)

                // upload architecture images
                for ((manifest, manifestDigest, blobs) in uploadContainerImage.images) {
                    // upload blobs in parallel, but only up to three at once to mimic how Docker does it
                    coroutineScope {
                        val semaphore = Semaphore(3)

                        for (blob in blobs.distinctBy(BlobPath::digest)) {
                            launch {
                                semaphore.withPermit {
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
                handleError(e)
            } finally {
                // clear up temporary blob files
                runCatching { cleanupTempDirectory(tempDirectory) }
                    .onFailure { t ->
                        logger.info(t) { "Failed to fully cleanup temp directory: ${tempDirectory.pathString}" }
                    }
            }
        }

    private suspend fun uploadBlob(repository: Repository, blob: BlobPath, mode: UploadMode) {
        if (!client.existsBlob(repository, blob.digest)) {
            val session = client.initiateBlobUpload(repository)

            val endSession = when (mode) {
                is UploadMode.Stream -> client.uploadBlobStream(session, blob.path, blob.size)
                is UploadMode.Chunks -> client.uploadBlobChunks(session, blob.path, mode.chunkSize)
            }

            client.finishBlobUpload(endSession, blob.digest)
        }
    }

    private fun handleError(e: Exception): Nothing {
        val sanitizedError = when (e) {
            is KircException, is RegistryException -> e

            else -> KircException.UnexpectedError(
                "Unexpected error, could not upload image to registry: ${e.cause}",
                e,
            )
        }
        logger.error(sanitizedError) { "Error uploading image to registry" }
        throw sanitizedError
    }

    private suspend fun cleanupTempDirectory(tempDirectory: Path) {
        withContext(Dispatchers.IO) {
            // current implementation stores blobs flatly; still, iterate and delete defensively
            SystemFileSystem.list(tempDirectory.toKotlinPath()).forEach(SystemFileSystem::delete)
            SystemFileSystem.delete(tempDirectory.toKotlinPath())
        }
    }
}
