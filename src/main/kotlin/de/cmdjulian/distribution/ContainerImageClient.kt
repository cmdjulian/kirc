package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.Blob
import de.cmdjulian.distribution.model.ContainerImage
import de.cmdjulian.distribution.model.Tag
import de.cmdjulian.distribution.spec.image.ImageConfig
import de.cmdjulian.distribution.spec.manifest.ManifestSingle
import kotlinx.coroutines.runBlocking

interface ContainerImageClient {
    /**
     * Get a list of tags for a certain repository.
     */
    fun tags(): List<Tag>

    /**
     * Returns the Manifest.
     */
    fun manifest(): ManifestSingle

    /**
     * Get the config of an Image.
     */
    fun config(): ImageConfig

    /**
     * Get the blobs of an Image.
     */
    fun blobs(): List<Blob>

    /**
     * Retrieves the images compressed size in bytes.
     */
    fun size(): ULong

    /**
     * Retrieve a completed Container Image.
     */
    fun toImage(): ContainerImage
}

interface AsyncContainerImageClient {
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
    suspend fun blobs(): List<Blob>

    /**
     * Retrieves the images compressed size in bytes.
     */
    suspend fun size(): ULong

    /**
     * Retrieve a completed Container Image.
     */
    suspend fun toImage(): ContainerImage
}

fun AsyncContainerImageClient.toBlockingClient() = object : ContainerImageClient {
    override fun tags(): List<Tag> = runBlocking { this@toBlockingClient.tags() }
    override fun manifest(): ManifestSingle = runBlocking { this@toBlockingClient.manifest() }
    override fun config(): ImageConfig = runBlocking { this@toBlockingClient.config() }
    override fun blobs(): List<Blob> = runBlocking { this@toBlockingClient.blobs() }
    override fun size(): ULong = runBlocking { this@toBlockingClient.size() }
    override fun toImage(): ContainerImage = runBlocking { this@toBlockingClient.toImage() }
}
