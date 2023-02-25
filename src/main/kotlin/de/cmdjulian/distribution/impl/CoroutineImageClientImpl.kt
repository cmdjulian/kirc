package de.cmdjulian.distribution.impl

import de.cmdjulian.distribution.CoroutineContainerRegistryClient
import de.cmdjulian.distribution.CoroutineImageClient
import de.cmdjulian.distribution.model.Blob
import de.cmdjulian.distribution.model.ContainerImage
import de.cmdjulian.distribution.model.ContainerImageName
import de.cmdjulian.distribution.model.Tag
import de.cmdjulian.distribution.spec.image.ImageConfig
import de.cmdjulian.distribution.spec.manifest.ManifestSingle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

internal class CoroutineImageClientImpl(
    private val client: CoroutineContainerRegistryClient,
    private val image: ContainerImageName,
    private val manifest: ManifestSingle = runBlocking {
        client.manifest(image.repository, image.reference) as ManifestSingle
    },
) : CoroutineImageClient {

    override suspend fun tags(): List<Tag> = client.tags(image.repository)

    override suspend fun manifest(): ManifestSingle = manifest

    override suspend fun config(): ImageConfig = client.config(image.repository, manifest)

    override suspend fun blobs(): List<Blob> = coroutineScope {
        manifest.layers
            .map { layer -> async { client.blob(image.repository, layer.digest) } }
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
