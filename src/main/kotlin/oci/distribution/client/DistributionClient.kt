package oci.distribution.client

import oci.distribution.client.model.domain.Reference
import oci.distribution.client.model.domain.Repository
import oci.distribution.client.model.domain.Tag
import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2

interface DistributionClient {

    /**
     * Checks if the registry is reachable and configured correctly.
     */
    fun testConnection(): Result<Unit>

    /**
     * Get a list of repositories the registry holds.
     */
    fun repositories(limit: Int? = null, last: Int? = null): List<Repository>

    /**
     * Get a list of tags for a certain repository.
     */
    fun tags(repository: Repository, limit: Int? = null, last: Int? = null): List<Tag>

    /**
     * Retrieve a manifest.
     */
    fun manifest(repository: Repository, reference: Reference): ManifestV2

    /**
     * Get the config of an Image.
     */
    fun imageConfig(repository: Repository, reference: Reference): ImageConfig
}
