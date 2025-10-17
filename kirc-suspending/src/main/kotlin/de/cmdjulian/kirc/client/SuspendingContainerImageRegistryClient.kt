package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.response.ResultSource
import de.cmdjulian.kirc.impl.response.UploadSession
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.io.Sink
import kotlinx.io.Source
import java.nio.file.Path

/**
 * Handles calls to the container registry and returns the result upon success.
 *
 * Either methods throw an error upon request failure or return the result.
 */
interface SuspendingContainerImageRegistryClient {
    /**
     * Checks if the registry is reachable and configured correctly. If not, a detailed Exception is thrown.
     */
    suspend fun testConnection()

    /**
     * Checks if registry contains a blob identified by [digest] in [repository]
     */
    suspend fun existsBlob(repository: Repository, digest: Digest): Boolean

    /**
     * Get a list of repositories the registry holds.
     */
    suspend fun repositories(limit: Int? = null, last: Int? = null): List<Repository>

    /**
     * Get a list of tags for a certain repository.
     */
    suspend fun tags(repository: Repository, limit: Int? = null, last: Int? = null): List<Tag>

    /** Get a list of tags containing the digest in provided repository */
    suspend fun tags(repository: Repository, digest: Digest): List<Tag>

    /**
     * Check if the image with the reference exists.
     */
    suspend fun exists(repository: Repository, reference: Reference): Boolean

    /**
     * Retrieve a manifest.
     */
    suspend fun manifest(repository: Repository, reference: Reference): Manifest

    /**
     * Retrieve manifest as stream
     */
    suspend fun manifestStream(repository: Repository, reference: Reference): ResultSource

    /**
     * Get the digest of the manifest for the provided tag.
     */
    suspend fun manifestDigest(repository: Repository, reference: Reference): Digest

    /**
     * Delete a manifest.
     */
    suspend fun manifestDelete(repository: Repository, reference: Reference): Digest

    /**
     * Get the config of an Image by its Manifest.
     */
    suspend fun config(repository: Repository, manifest: ManifestSingle): ImageConfig

    /**
     * Get the config of an Image by its reference.
     * This method should only be used, if you know, that the underlying image identified by [manifestReference] is not a
     * ManifestList and is identified uniquely.
     * If the [manifestReference] points to a ManifestList, the behaviour is up to the registry. Usually the first entry of the
     * list is returned.
     *
     * To be safe, it's better to use [config] instead.
     */
    suspend fun config(repository: Repository, manifestReference: Reference): ImageConfig

    /**
     * Retrieve a Blob for an image as [ByteArray]
     *
     * Loads data into memory
     */
    suspend fun blob(repository: Repository, digest: Digest): ByteArray

    /**
     *  Retrieve a Blob for an image as [Source] data stream
     *
     *  Data not directly loaded into memory
     */
    suspend fun blobStream(repository: Repository, digest: Digest): Source

    /**
     * Initiate data upload
     *
     * @return the upload session id and location
     */
    suspend fun initiateBlobUpload(repository: Repository): UploadSession

    /**
     * Uploads an entire blob chunk-wise for reduced memory load
     *
     * [chunkSize] - Chunk Size in Bytes, defaulting to 10 MiB
     */
    suspend fun uploadBlobChunks(session: UploadSession, path: Path, chunkSize: Long = 10 * 1048576L): UploadSession

    /**
     * Uploads an entire blob by stream
     */
    suspend fun uploadBlobStream(session: UploadSession, stream: Source): UploadSession

    /**
     * Upload a manifest
     */
    suspend fun uploadManifest(repository: Repository, reference: Reference, manifest: Manifest): Digest

    /** Finishes blob upload session */
    suspend fun finishBlobUpload(session: UploadSession, digest: Digest): Digest

    /**
     * Returns the provided [session] current upload status from start (inclusive) to end (exclusive) in bytes
     */
    suspend fun uploadStatus(session: UploadSession): Pair<Long, Long>

    /**
     * Cancels the ongoing upload of blobs for certain session id
     */
    suspend fun cancelBlobUpload(session: UploadSession)

    /**
     * Uploads [tar] image archive to container registry at [repository] with [reference]
     *
     * @return the digest of uploaded image
     */
    suspend fun upload(repository: Repository, reference: Reference, tar: Source): Digest

    /**
     * Downloads a docker image for certain [reference].
     *
     * Downloads from registry and temporarily stores data in temp directory
     *
     * For [reference] we download everything to what [reference] directs to (either [ManifestSingle] or [ManifestList])
     */
    suspend fun download(repository: Repository, reference: Reference): Source

    /**
     * Downloads a docker image for certain [reference]
     *
     * Downloads from registry and writes directly to [destination]
     *
     * For [reference] we download everything to what [reference] directs to (either [ManifestSingle] or [ManifestList])
     */
    suspend fun download(repository: Repository, reference: Reference, destination: Sink)

    /**
     * Convert general Client to DockerImageClient.
     */
    suspend fun toImageClient(repository: Repository, reference: Reference): SuspendingContainerImageClient

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle,
    ): SuspendingContainerImageClient
}
