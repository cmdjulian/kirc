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
import kotlinx.coroutines.runBlocking
import kotlinx.io.asInputStream
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.InputStream
import java.io.OutputStream

interface BlockingContainerImageRegistryClient {
    /**
     * Checks if the registry is reachable and configured correctly. If not, a detailed Exception is thrown.
     */
    fun testConnection()

    /**
     * Get a list of repositories the registry holds.
     */
    fun repositories(limit: Int? = null, last: Int? = null): List<Repository>

    /**
     * Get a list of tags for a certain repository.
     */
    fun tags(repository: Repository, limit: Int? = null, last: Int? = null): List<Tag>

    /**
     * Check if the image with the reference exists.
     */
    fun exists(repository: Repository, reference: Reference): Boolean

    /**
     * Retrieve a manifest.
     */
    fun manifest(repository: Repository, reference: Reference): Manifest

    /**
     * Get the digest of the manifest for the provided tag.
     */
    fun manifestDigest(repository: Repository, reference: Reference): Digest

    /**
     * Delete manifest.
     */
    fun manifestDelete(repository: Repository, reference: Reference): Digest

    /**
     * Get the config of an Image by its Manifest.
     */
    fun config(repository: Repository, manifest: ManifestSingle): ImageConfig

    /**
     * Get the config of an Image by its reference.
     * This method should only be used, if you know, that the underlying image identified by [reference] is not a
     * ManifestList and is identified uniquely.
     * If the [reference] points to a ManifestList, the behaviour is up to the registry. Usually the first entry of the
     * list is returned.
     *
     * To be safe, it's better to use [Digest] or config([Repository], [ManifestSingle]) instead.
     */
    fun config(repository: Repository, reference: Reference): ImageConfig

    /**
     * Retrieve a Blob for an image.
     */
    fun blob(repository: Repository, digest: Digest): ByteArray

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle? = null,
    ): BlockingContainerImageClient

    /**
     * Uploads [tar] image archive to container registry at [repository] with [reference]
     *
     * @return the digest of uploaded image
     */
    fun upload(repository: Repository, reference: Reference, tar: InputStream, mode: UploadMode = UploadMode.Stream): Digest

    /**
     * Downloads a docker image for certain [reference].
     *
     * For [reference] we download everything to what [reference] directs to (either [ManifestSingle] or [ManifestList])
     */
    fun download(repository: Repository, reference: Reference): InputStream

    fun download(repository: Repository, reference: Reference, destination: OutputStream)
}

fun SuspendingContainerImageRegistryClient.toBlockingClient() = object : BlockingContainerImageRegistryClient {
    override fun testConnection(): Unit = runBlocking(Dispatchers.Default) { this@toBlockingClient.testConnection() }

    override fun repositories(limit: Int?, last: Int?): List<Repository> =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.repositories(limit, last) }

    override fun tags(repository: Repository, limit: Int?, last: Int?): List<Tag> =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.tags(repository, limit, last) }

    override fun exists(repository: Repository, reference: Reference): Boolean =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.exists(repository, reference) }

    override fun manifest(repository: Repository, reference: Reference): Manifest =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.manifest(repository, reference) }

    override fun manifestDigest(repository: Repository, reference: Reference): Digest =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.manifestDigest(repository, reference) }

    override fun manifestDelete(repository: Repository, reference: Reference): Digest =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.manifestDelete(repository, reference) }

    override fun config(repository: Repository, manifest: ManifestSingle): ImageConfig =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.config(repository, manifest) }

    override fun config(repository: Repository, reference: Reference): ImageConfig =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.config(repository, reference) }

    override fun blob(repository: Repository, digest: Digest): ByteArray =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.blob(repository, digest) }

    override fun toImageClient(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle?,
    ): BlockingContainerImageClient {
        val client = if (manifest == null) {
            runBlocking(Dispatchers.Default) { this@toBlockingClient.toImageClient(repository, reference) }
        } else {
            this@toBlockingClient.toImageClient(repository, reference, manifest)
        }

        return client.toBlockingClient()
    }

    override fun upload(repository: Repository, reference: Reference, tar: InputStream, mode: UploadMode): Digest =
        runBlocking(Dispatchers.Default) {
            this@toBlockingClient.upload(
                repository,
                reference,
                tar.asSource().buffered(),
                mode
            )
        }

    override fun download(repository: Repository, reference: Reference): InputStream =
        runBlocking(Dispatchers.Default) { this@toBlockingClient.download(repository, reference).asInputStream() }

    override fun download(repository: Repository, reference: Reference, destination: OutputStream) =
        runBlocking(Dispatchers.Default) {
            this@toBlockingClient.download(repository, reference, destination.asSink().buffered())
        }
}
