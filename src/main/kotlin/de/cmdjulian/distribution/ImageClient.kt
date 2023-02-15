package de.cmdjulian.distribution

import de.cmdjulian.distribution.spec.image.docker.ImageV1
import de.cmdjulian.distribution.spec.manifest.DockerManifestV2
import de.cmdjulian.distribution.model.Blob
import de.cmdjulian.distribution.model.DockerImage
import de.cmdjulian.distribution.model.Tag

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
    suspend fun manifest(): Result<DockerManifestV2>

    /**
     * Get the config of an Image.
     */
    suspend fun config(): Result<ImageV1>

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
