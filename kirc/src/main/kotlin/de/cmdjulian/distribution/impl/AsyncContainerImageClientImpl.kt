package de.cmdjulian.distribution.impl

import de.cmdjulian.distribution.AsyncContainerImageClient
import de.cmdjulian.distribution.AsyncContainerImageRegistryClient
import de.cmdjulian.distribution.model.ContainerImage
import de.cmdjulian.distribution.model.ContainerImageName
import de.cmdjulian.distribution.model.LayerBlob
import de.cmdjulian.distribution.model.Tag
import de.cmdjulian.distribution.spec.image.ImageConfig
import de.cmdjulian.distribution.spec.manifest.ManifestSingle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class AsyncContainerImageClientImpl(
    private val client: AsyncContainerImageRegistryClient,
    private val image: ContainerImageName,
    private val manifest: ManifestSingle,
) : AsyncContainerImageClient {
    companion object {
        suspend operator fun invoke(client: AsyncContainerImageRegistryClient, image: ContainerImageName) =
            AsyncContainerImageClientImpl(
                client,
                image,
                client.manifest(image.repository, image.reference) as ManifestSingle,
            )
    }

    override suspend fun tags(): List<Tag> = client.tags(image.repository)

    override suspend fun manifest(): ManifestSingle = manifest

    override suspend fun config(): ImageConfig = client.config(image.repository, manifest)

    override suspend fun blobs(): List<LayerBlob> = coroutineScope {
        // limit concurrent pull of layers to three at a time, like Docker does it
        val semaphore = Semaphore(3)

        manifest.layers
            .map { layer ->
                async {
                    val blob = semaphore.withPermit { client.blob(image.repository, layer.digest) }
                    LayerBlob(layer.digest, layer.mediaType, blob)
                }
            }
            .awaitAll()
    }

    override suspend fun size(): ULong = with(manifest) {
        config.size + layers.sumOf { layer -> layer.size.toLong() }.toULong()
    }

    override suspend fun toImage(): ContainerImage = coroutineScope {
        val config = async { config() }
        val blobs = async { blobs() }

        ContainerImage(manifest, config.await(), blobs.await())
    }
}
