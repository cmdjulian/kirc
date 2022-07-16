package oci.distribution.client.api

import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2
import oci.distribution.client.model.oci.Digest
import oci.distribution.client.model.oci.Reference
import oci.distribution.client.model.oci.Repository
import oci.distribution.client.model.oci.Tag

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

    suspend fun exists(repository: Repository, reference: Reference): Result<Boolean>

    /**
     * Retrieve a manifest.
     */
    suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestV2>

    suspend fun manifestDigest(repository: Repository, tag: Tag): Result<Digest>

    suspend fun deleteManifest(repository: Repository, reference: Reference): Result<*>

    /**
     * Get the config of an Image.
     */
    suspend fun config(repository: Repository, reference: Reference): Result<ImageConfig>

    /**
     * Retrieve a Blob for an image. The Map contains not just the Blobs byte, but also it's content type.
     */
    suspend fun blob(repository: Repository, digest: Digest): Result<Pair<String, ByteArray>>

    suspend fun deleteBlob(repository: Repository, digest: Digest): Result<*>
}
