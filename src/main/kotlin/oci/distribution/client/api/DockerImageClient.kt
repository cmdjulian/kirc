package oci.distribution.client.api

import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2
import oci.distribution.client.model.oci.DockerImage
import oci.distribution.client.model.oci.Tag

interface DockerImageClient {

    /**
     * Check if an image exists
     */
    suspend fun exists(): Boolean

    /**
     * Get a list of tags for a certain repository.
     */
    suspend fun tags(limit: Int? = null, last: Int? = null): Result<List<Tag>>

    /**
     * Retrieve a manifest.
     */
    suspend fun manifest(): Result<ManifestV2>

    /**
     * Get the config of an Image.
     */
    suspend fun config(): Result<ImageConfig>

    /**
     * Deletes a given Docker image.
     */
    suspend fun delete(): Result<*>

    /**
     * Retrieves the images compressed size in bytes.
     */
    suspend fun size(): Result<Long>

    /**
     * Retrieve a Docker Image.
     */
    suspend fun toDockerImage(): Result<DockerImage>
}
