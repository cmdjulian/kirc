package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.image.ImageConfigV1
import de.cmdjulian.distribution.model.manifest.docker.ManifestV2
import de.cmdjulian.distribution.model.oci.Blob
import de.cmdjulian.distribution.model.oci.DockerImage
import de.cmdjulian.distribution.model.oci.Tag

interface ImageClient {

    /**
     * Check if an image exists
     */
    suspend fun exists(): Result<Boolean>

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
    suspend fun config(): Result<ImageConfigV1>

    /**
     * Get the config of an Image.
     */
    suspend fun blobs(): Result<List<Blob>>

    /**
     * Deletes a given Docker image.
     */
    suspend fun delete(): Result<*>

    /**
     * Retrieves the images compressed size in bytes.
     */
    suspend fun size(): Result<UInt>

    /**
     * Retrieve a Docker Image.
     */
    suspend fun toDockerImage(): Result<DockerImage>
}
