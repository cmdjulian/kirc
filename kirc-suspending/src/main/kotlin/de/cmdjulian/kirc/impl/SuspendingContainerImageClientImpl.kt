package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.client.SuspendingContainerImageClient
import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.LayerBlob
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class SuspendingContainerImageClientImpl(
    private val client: SuspendingContainerImageRegistryClient,
    private val image: ContainerImageName,
    private val manifest: ManifestSingle,
) : SuspendingContainerImageClient {

    companion object {
        suspend operator fun invoke(client: SuspendingContainerImageRegistryClient, image: ContainerImageName) =
            SuspendingContainerImageClientImpl(
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

    override suspend fun size(): Long = manifest.config.size + manifest.layers.sumOf(LayerReference::size)

    override suspend fun toImage(): ContainerImage = coroutineScope {
        val config = async { config() }
        val blobs = async { blobs() }
        val digest = async { image.digest ?: client.manifestDigest(image.repository, image.reference) }

        ContainerImage(manifest, digest.await(), config.await(), blobs.await())
    }
}
