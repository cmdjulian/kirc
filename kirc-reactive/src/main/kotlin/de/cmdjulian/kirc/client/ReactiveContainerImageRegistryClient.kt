package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ReactiveContainerImageRegistryClient {
    /**
     * Checks if the registry is reachable and configured correctly. If not, a detailed Exception is thrown.
     */
    fun testConnection()

    /**
     * Get a list of repositories the registry holds.
     */
    fun repositories(limit: Int? = null, last: Int? = null): Flux<Repository>

    /**
     * Get a list of tags for a certain repository.
     */
    fun tags(repository: Repository, limit: Int? = null, last: Int? = null): Flux<Tag>

    /**
     * Check if the image with the reference exists.
     */
    fun exists(repository: Repository, reference: Reference): Mono<Boolean>

    /**
     * Retrieve a manifest.
     */
    fun manifest(repository: Repository, reference: Reference): Mono<Manifest>

    /**
     * Get the digest of the manifest for the provided tag.
     */
    fun manifestDigest(repository: Repository, tag: Tag): Mono<Digest>

    /**
     * Delete manifest.
     */
    fun manifestDelete(repository: Repository, reference: Reference): Mono<Digest>

    /**
     * Get the config of an Image by its Manifest.
     */
    fun config(repository: Repository, manifest: ManifestSingle): Mono<ImageConfig>

    /**
     * Recursively finds the config of provided image manifest.
     * Finds the first config which matches current platform.
     *
     * If no matching Platform found, throws [de.cmdjulian.kirc.exception.KircException.PlatformNotMatching]
     */
    fun config(repository: Repository, reference: Reference): Mono<ImageConfig>

    /**
     * Retrieve a Blob for an image.
     */
    fun blob(repository: Repository, digest: Digest): Mono<ByteArray>

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle? = null,
    ): Mono<ReactiveContainerImageClient>

    /**
     * Uploads [tar] image archive to container registry at [repository] with [reference]
     *
     * @return the digest of uploaded image
     */
    fun upload(repository: Repository, reference: Reference, tar: Flux<Byte>, uploadMode: UploadMode): Mono<Digest>

    /**
     * Downloads a docker image for certain [reference].
     *
     * For [reference] we download everything to what [reference] directs to (either [ManifestSingle] or [ManifestList])
     */
    fun download(repository: Repository, reference: Reference): Flux<Byte>
}

fun SuspendingContainerImageRegistryClient.toReactiveClient() = object : ReactiveContainerImageRegistryClient {
    override fun testConnection(): Unit = runBlocking { this@toReactiveClient.testConnection() }

    override fun repositories(limit: Int?, last: Int?): Flux<Repository> =
        flux { this@toReactiveClient.repositories(limit, last).forEach { send(it) } }

    override fun tags(repository: Repository, limit: Int?, last: Int?): Flux<Tag> =
        flux { this@toReactiveClient.tags(repository, limit, last).forEach { send(it) } }

    override fun exists(repository: Repository, reference: Reference): Mono<Boolean> =
        mono { this@toReactiveClient.exists(repository, reference) }

    override fun manifest(repository: Repository, reference: Reference): Mono<Manifest> =
        mono { this@toReactiveClient.manifest(repository, reference) }

    override fun manifestDigest(repository: Repository, tag: Tag): Mono<Digest> =
        mono { this@toReactiveClient.manifestDigest(repository, tag) }

    override fun manifestDelete(repository: Repository, reference: Reference): Mono<Digest> =
        mono { this@toReactiveClient.manifestDelete(repository, reference) }

    override fun config(repository: Repository, manifest: ManifestSingle): Mono<ImageConfig> =
        mono { this@toReactiveClient.config(repository, manifest) }

    override fun config(repository: Repository, reference: Reference): Mono<ImageConfig> =
        mono { this@toReactiveClient.config(repository, reference) }

    override fun blob(repository: Repository, digest: Digest): Mono<ByteArray> =
        mono { this@toReactiveClient.blob(repository, digest) }

    override fun toImageClient(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle?,
    ): Mono<ReactiveContainerImageClient> = if (manifest == null) {
        mono { this@toReactiveClient.toImageClient(repository, reference).toReactiveClient() }
    } else {
        Mono.just(this@toReactiveClient.toImageClient(repository, reference, manifest).toReactiveClient())
    }

    override fun upload(
        repository: Repository,
        reference: Reference,
        tar: Flux<Byte>,
        uploadMode: UploadMode,
    ): Mono<Digest> = mono {
        val buffer = Buffer().also { buffer -> tar.collect(buffer::writeByte) }
        this@toReactiveClient.upload(repository, reference, buffer, uploadMode)
    }

    override fun download(repository: Repository, reference: Reference): Flux<Byte> = flux {
        this@toReactiveClient.download(repository, reference).use { result ->
            withContext(Dispatchers.IO) {
                while (!result.exhausted()) {
                    send(result.readByte())
                }
            }
        }
    }
}
