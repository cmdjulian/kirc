package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.image.ImageConfigV1
import de.cmdjulian.distribution.model.manifest.docker.ManifestV2
import de.cmdjulian.distribution.model.oci.Blob
import de.cmdjulian.distribution.model.oci.Digest
import de.cmdjulian.distribution.model.oci.Reference
import de.cmdjulian.distribution.model.oci.Repository
import de.cmdjulian.distribution.model.oci.Tag

interface DistributionClient {

    /**
     * Checks if the registry is reachable and configured correctly.
     */
    suspend fun testConnection(): Boolean

    /**
     * Get a list of repositories the registry holds.
     */
    suspend fun repositories(limit: Int? = null, last: Int? = null): Result<List<Repository>>

    /**
     * Get a list of tags for a certain repository.
     */
    suspend fun tags(repository: Repository, limit: Int? = null, last: Int? = null): Result<List<Tag>>

    /**
     * Check if the image with the reference exists.
     */
    suspend fun exists(repository: Repository, reference: Reference): Result<Boolean>

    /**
     * Retrieve a manifest.
     */
    suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestV2>

    /**
     * Get the digest of the manifest for the provided tag.
     */
    suspend fun manifestDigest(repository: Repository, tag: Tag): Result<Digest>

    /**
     * Delete manifest. Remember to delete the in the manifest referenced layers first.
     */
    suspend fun deleteManifest(repository: Repository, reference: Reference): Result<*>

    /**
     * Get the config of an Image.
     */
    suspend fun config(repository: Repository, reference: Reference): Result<ImageConfigV1>

    /**
     * Retrieve a Blob for an image. The Map contains not just the Blobs byte, but also it's content type.
     */
    suspend fun blob(repository: Repository, digest: Digest): Result<Blob>

    /**
     * Delete the provided blob.
     */
    suspend fun deleteBlob(repository: Repository, digest: Digest): Result<*>

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(repository: Repository, reference: Reference? = null): ImageClient
}
