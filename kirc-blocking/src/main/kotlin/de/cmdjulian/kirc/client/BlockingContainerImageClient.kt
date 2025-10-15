package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.LayerBlob
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

interface BlockingContainerImageClient {
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
    fun blobs(): List<LayerBlob>

    /**
     * Retrieves the images compressed size in bytes.
     */
    fun size(): Long

    /**
     * Retrieve a completed Container Image.
     */
    fun toImage(): ContainerImage
}

fun SuspendingContainerImageClient.toBlockingClient() = object : BlockingContainerImageClient {
    override fun tags(): List<Tag> = runBlocking(Dispatchers.Default) { this@toBlockingClient.tags() }
    override fun manifest(): ManifestSingle = runBlocking(Dispatchers.Default) { this@toBlockingClient.manifest() }
    override fun config(): ImageConfig = runBlocking(Dispatchers.Default) { this@toBlockingClient.config() }
    override fun blobs(): List<LayerBlob> = runBlocking(Dispatchers.Default) { this@toBlockingClient.blobs() }
    override fun size(): Long = runBlocking(Dispatchers.Default) { this@toBlockingClient.size() }
    override fun toImage(): ContainerImage = runBlocking(Dispatchers.Default) { this@toBlockingClient.toImage() }
}
