package de.cmdjulian.distribution

import de.cmdjulian.distribution.spec.image.docker.ImageV1
import de.cmdjulian.distribution.spec.manifest.docker.ManifestV2
import de.cmdjulian.distribution.model.Blob
import de.cmdjulian.distribution.model.DockerImage
import de.cmdjulian.distribution.model.Tag

interface BlockingImageClient {

    /**
     * Check if an image exists
     */
    fun exists(): Result<Boolean>

    /**
     * Get a list of tags for a certain repository.
     */
    fun tags(limit: Int? = null, last: Int? = null): Result<List<Tag>>

    /**
     * Retrieve a manifest.
     */
    fun manifest(): Result<ManifestV2>

    /**
     * Get the config of an Image.
     */
    fun config(): Result<ImageV1>

    /**
     * Get the config of an Image.
     */
    fun blobs(): Result<List<Blob>>

    /**
     * Deletes a given Docker image.
     */
    fun delete(): Result<*>

    /**
     * Retrieves the images compressed size in bytes.
     */
    fun size(): Result<UInt>

    /**
     * Retrieve a Docker Image.
     */
    fun toDockerImage(): Result<DockerImage>
}
