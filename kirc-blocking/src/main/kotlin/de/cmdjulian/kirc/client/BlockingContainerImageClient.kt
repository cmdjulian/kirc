package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.LayerBlob
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.coroutines.runBlocking

@Suppress("INAPPLICABLE_JVM_NAME")
interface BlockingContainerImageClient {
    /**
     * Get a list of tags for a certain repository.
     */
    @JvmName("tags")
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
    override fun tags(): List<Tag> = runBlocking { this@toBlockingClient.tags() }
    override fun manifest(): ManifestSingle = runBlocking { this@toBlockingClient.manifest() }
    override fun config(): ImageConfig = runBlocking { this@toBlockingClient.config() }
    override fun blobs(): List<LayerBlob> = runBlocking { this@toBlockingClient.blobs() }
    override fun size(): Long = runBlocking { this@toBlockingClient.size() }
    override fun toImage(): ContainerImage = runBlocking { this@toBlockingClient.toImage() }
}
