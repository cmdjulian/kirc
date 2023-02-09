package de.cmdjulian.distribution.impl

import de.cmdjulian.distribution.ImageClient
import de.cmdjulian.distribution.model.manifest.docker.ManifestV2
import de.cmdjulian.distribution.model.oci.Blob
import de.cmdjulian.distribution.model.oci.DockerImage
import de.cmdjulian.distribution.model.oci.DockerImageSlug
import de.cmdjulian.distribution.utils.foldSuspend
import de.cmdjulian.distribution.utils.pmap
import de.cmdjulian.distribution.utils.zip
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class ImageClientImpl(private val client: DistributionClientImpl, private val image: DockerImageSlug) :
    ImageClient {

    override suspend fun exists() = client.exists(image.repository, image.reference)

    override suspend fun tags(limit: Int?, last: Int?) = client.tags(image.repository)

    override suspend fun manifest() = client.manifest(image.repository, image.reference)

    override suspend fun config() = client.config(image.repository, image.reference)

    override suspend fun blobs() = blobs(manifest())

    private suspend fun blobs(manifest: Result<ManifestV2>): Result<List<Blob>> {
        return manifest.map(ManifestV2::layers).foldSuspend { layers ->
            layers.pmap { layer -> client.blob(image.repository, layer.digest) }.zip()
        }
    }

    override suspend fun delete(): Result<*> = manifest()
        .foldSuspend { manifest -> client.deleteManifest(image.repository, image.reference).map { manifest } }
        .foldSuspend { it.layers.pmap { layer -> client.deleteBlob(image.repository, layer.digest) }.zip() }

    override suspend fun size(): Result<UInt> = manifest().map { (_, _, config, layers) ->
        config.size + layers.sumOf { layer -> layer.size.toLong() }.toUInt()
    }

    override suspend fun toDockerImage(): Result<DockerImage> {
        val manifest = manifest()
        val blobs = coroutineScope { async { blobs(manifest) } }
        val config = manifest.foldSuspend { client.config(image.repository, it.config.digest) }

        return runCatching { DockerImage(manifest.getOrThrow(), config.getOrThrow(), blobs.await().getOrThrow()) }
    }
}
