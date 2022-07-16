package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.image.ImageConfig
import de.cmdjulian.distribution.model.manifest.ManifestV2
import de.cmdjulian.distribution.model.oci.Blob
import de.cmdjulian.distribution.model.oci.DockerImage
import de.cmdjulian.distribution.model.oci.Tag

interface BlockingDockerImageClient {

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
    fun config(): Result<ImageConfig>

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
    fun size(): Result<Long>

    /**
     * Retrieve a Docker Image.
     */
    fun toDockerImage(): Result<DockerImage>
}
