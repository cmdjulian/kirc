package oci.distribution.client.impl

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import oci.distribution.client.api.DistributionClient
import oci.distribution.client.api.DockerImageClient
import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2
import oci.distribution.client.model.oci.DockerImage
import oci.distribution.client.model.oci.DockerImageSlug
import oci.distribution.client.model.oci.Tag
import oci.distribution.client.utils.foldSuspend
import oci.distribution.client.utils.zip

internal class DockerImageClientImpl(private val client: DistributionClient, private val image: DockerImageSlug) :
    DockerImageClient {

    override suspend fun exists(): Boolean {
        return manifest().isSuccess
    }

    override suspend fun tags(limit: Int?, last: Int?): Result<List<Tag>> {
        return client.tags(image.repository)
    }

    override suspend fun manifest(): Result<ManifestV2> {
        return client.manifest(image.repository, image.reference)
    }

    override suspend fun config(): Result<ImageConfig> {
        return client.config(image.repository, image.reference)
    }

    override suspend fun delete(): Result<*> {
        return manifest()
            .foldSuspend { manifest -> client.deleteManifest(image.repository, image.reference).map { manifest } }
            .foldSuspend { it.layers.map { layer -> client.deleteBlob(image.repository, layer.digest) }.zip() }
    }

    override suspend fun size(): Result<Long> {
        return manifest().map { (_, config, layers) ->
            config.size + layers.sumOf { layer -> layer.size.toLong() }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun toDockerImage(): Result<DockerImage> {
        val config = GlobalScope.async { config() }
        val manifest = manifest()
        val blobs = manifest.mapCatching { manifestV2 ->
            manifestV2.layers.associate { it.digest to GlobalScope.async { client.blob(image.repository, it.digest) } }
                .mapValues { (_, value) -> value.await() }
                .mapValues { (_, value) -> value.map { (_, content) -> content } }
                .mapValues { (_, value) -> value.getOrThrow() }
        }

        return config.await().mapCatching { DockerImage(manifest.getOrThrow(), it, blobs.getOrThrow()) }
    }
}
