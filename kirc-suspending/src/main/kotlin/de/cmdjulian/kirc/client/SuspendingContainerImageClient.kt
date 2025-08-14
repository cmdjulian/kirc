package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.LayerBlob
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestSingle

/**
 * Provides access to certain registry to allow handling docker images.
 */
interface SuspendingContainerImageClient {
    /**
     * Get a list of tags for a certain repository.
     */
    suspend fun tags(): List<Tag>

    /**
     * Returns the Manifest.
     */
    suspend fun manifest(): ManifestSingle

    /**
     * Get the config of an Image.
     */
    suspend fun config(): ImageConfig

    /**
     * Get the blobs of an Image.
     */
    suspend fun blobs(): List<LayerBlob>

    /**
     * Retrieves the images compressed size in bytes.
     */
    suspend fun size(): Long

    /**
     * Retrieve a completed Container Image.
     */
    suspend fun toImage(): ContainerImage
}
