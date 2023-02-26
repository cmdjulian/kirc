package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.client.SuspendingContainerImageClient
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.LayerBlob
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ReactiveContainerImageClient {
    /**
     * Get a list of tags for a certain repository.
     */
    suspend fun tags(): Flux<Tag>

    /**
     * Returns the Manifest.
     */
    suspend fun manifest(): Mono<ManifestSingle>

    /**
     * Get the config of an Image.
     */
    suspend fun config(): Mono<ImageConfig>

    /**
     * Get the blobs of an Image.
     */
    suspend fun blobs(): Flux<LayerBlob>

    /**
     * Retrieves the images compressed size in bytes.
     */
    suspend fun size(): Mono<ULong>

    /**
     * Retrieve a completed Container Image.
     */
    suspend fun toImage(): Mono<ContainerImage>
}

fun SuspendingContainerImageClient.toReactiveClient() = object : ReactiveContainerImageClient {
    override suspend fun tags(): Flux<Tag> = flux { this@toReactiveClient.tags().forEach { send(it) } }
    override suspend fun manifest(): Mono<ManifestSingle> = mono { this@toReactiveClient.manifest() }
    override suspend fun config(): Mono<ImageConfig> = mono { this@toReactiveClient.config() }
    override suspend fun blobs(): Flux<LayerBlob> = flux { this@toReactiveClient.blobs().forEach { send(it) } }
    override suspend fun size(): Mono<ULong> = mono { this@toReactiveClient.size() }
    override suspend fun toImage(): Mono<ContainerImage> = mono { this@toReactiveClient.toImage() }
}
