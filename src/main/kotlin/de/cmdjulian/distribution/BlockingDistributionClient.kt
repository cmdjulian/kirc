package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.image.ImageConfigV1
import de.cmdjulian.distribution.model.manifest.docker.ManifestV2
import de.cmdjulian.distribution.model.oci.Blob
import de.cmdjulian.distribution.model.oci.Digest
import de.cmdjulian.distribution.model.oci.Reference
import de.cmdjulian.distribution.model.oci.Repository
import de.cmdjulian.distribution.model.oci.Tag

interface BlockingDistributionClient {

    /**
     * Checks if the registry is reachable and configured correctly.
     */
    fun testConnection(): Boolean

    /**
     * Get a list of repositories the registry holds.
     */
    fun repositories(limit: Int? = null, last: Int? = null): Result<List<Repository>>

    /**
     * Get a list of tags for a certain repository.
     */
    fun tags(repository: Repository, limit: Int? = null, last: Int? = null): Result<List<Tag>>

    /**
     * Check if the image with the reference exists.
     */
    fun exists(repository: Repository, reference: Reference): Result<Boolean>

    /**
     * Retrieve a manifest.
     */
    fun manifest(repository: Repository, reference: Reference): Result<ManifestV2>

    /**
     * Get the digest of the manifest for the provided tag.
     */
    fun manifestDigest(repository: Repository, tag: Tag): Result<Digest>

    /**
     * Delete manifest. Remember to delete the in the manifest referenced layers first.
     */
    fun deleteManifest(repository: Repository, reference: Reference): Result<*>

    /**
     * Get the config of an Image.
     */
    fun config(repository: Repository, reference: Reference): Result<ImageConfigV1>

    /**
     * Retrieve a Blob for an image. The Map contains not just the Blobs byte, but also it's content type.
     */
    fun blob(repository: Repository, digest: Digest): Result<Blob>

    /**
     * Delete the provided blob.
     */
    fun deleteBlob(repository: Repository, digest: Digest): Result<*>

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(repository: Repository, reference: Reference? = null): BlockingImageClient
}
