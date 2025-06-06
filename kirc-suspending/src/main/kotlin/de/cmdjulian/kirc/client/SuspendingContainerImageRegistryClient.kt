package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import java.util.*

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

    /**
     * Check if the image with the reference exists.
     */
    suspend fun exists(repository: Repository, reference: Reference): Boolean

    /**
     * Retrieve a manifest.
     */
    suspend fun manifest(repository: Repository, reference: Reference): Manifest

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
     * This method should only be used, if you know, that the underlying image identified by [reference] is not a
     * ManifestList and is identified uniquely.
     * If the [reference] points to a ManifestList, the behaviour is up to the registry. Usually the first entry of the
     * list is returned.
     *
     * To be safe, it's better to use [config] instead.
     */
    suspend fun config(repository: Repository, reference: Reference): ImageConfig

    /**
     * Retrieve a Blob for an image.
     */
    suspend fun blob(repository: Repository, digest: Digest): ByteArray

    /**
     * Convert general Client to DockerImageClient.
     */
    suspend fun toImageClient(repository: Repository, reference: Reference): SuspendingContainerImageClient

    /**
     * Initiate data upload
     *
     * @return the upload session id or null if the mount was successful
     */
    suspend fun initiateUpload(repository: Repository, from: Repository?, mount: Digest?): UUID?

    /**
     * Upload a blob or finish the chunked upload of a blob
     */
    suspend fun uploadBlob(repository: Repository, uploadUUID: UUID, digest: Digest, blob: ByteArray?): Digest

    /**
     * Upload a manifest
     */
    suspend fun uploadManifest(repository: Repository, reference: Reference, manifest: ManifestSingle): Digest

    suspend fun cancelBlobUpload(repository: Repository, sessionUUID: UUID)

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle,
    ): SuspendingContainerImageClient
}
