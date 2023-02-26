package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.ContainerImageName
import de.cmdjulian.distribution.model.Digest
import de.cmdjulian.distribution.model.Reference
import de.cmdjulian.distribution.model.Repository
import de.cmdjulian.distribution.model.Tag
import de.cmdjulian.distribution.spec.image.ImageConfig
import de.cmdjulian.distribution.spec.manifest.Manifest
import de.cmdjulian.distribution.spec.manifest.ManifestSingle
import kotlinx.coroutines.runBlocking

@JvmDefaultWithCompatibility
@Suppress("OVERLOADS_INTERFACE")
interface ContainerImageRegistryClient {
    /**
     * Checks if the registry is reachable and configured correctly. If not, a detailed Exception is thrown.
     */
    fun testConnection()

    /**
     * Get a list of repositories the registry holds.
     */
    @JvmOverloads
    fun repositories(limit: Int? = null, last: Int? = null): List<Repository>

    /**
     * Get a list of tags for a certain repository.
     */
    @JvmOverloads
    fun tags(repository: Repository, limit: Int? = null, last: Int? = null): List<Tag>

    /**
     * Check if the image exists.
     */
    fun exists(image: ContainerImageName): Boolean = exists(image.repository, image.reference)

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
    fun manifestDigest(image: ContainerImageName): Digest =
        image.digest ?: manifestDigest(image.repository, image.tag!!)

    /**
     * Get the digest of the manifest for the provided tag.
     */
    fun manifestDigest(repository: Repository, tag: Tag): Digest

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
     * To be safe, it's better to use [config] instead.
     */
    fun config(image: ContainerImageName): ImageConfig = config(image.repository, image.reference)

    /**
     * Get the config of an Image by its reference.
     * This method should only be used, if you know, that the underlying image identified by [reference] is not a
     * ManifestList and is identified uniquely.
     * If the [reference] points to a ManifestList, the behaviour is up to the registry. Usually the first entry of the
     * list is returned.
     *
     * To be safe, it's better to use [config] instead.
     */
    fun config(repository: Repository, reference: Reference): ImageConfig

    /**
     * Retrieve a Blob for an image.
     */
    fun blob(repository: Repository, digest: Digest): ByteArray

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(image: ContainerImageName): ContainerImageClient

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(image: ContainerImageName, manifest: ManifestSingle): ContainerImageClient
}

interface AsyncContainerImageRegistryClient {
    /**
     * Checks if the registry is reachable and configured correctly. If not, a detailed Exception is thrown.
     */
    suspend fun testConnection()

    /**
     * Get a list of repositories the registry holds.
     */
    suspend fun repositories(limit: Int? = null, last: Int? = null): List<Repository>

    /**
     * Get a list of tags for a certain repository.
     */
    suspend fun tags(repository: Repository, limit: Int? = null, last: Int? = null): List<Tag>

    /**
     * Check if the image exists.
     */
    suspend fun exists(image: ContainerImageName): Boolean = exists(image.repository, image.reference)

    /**
     * Check if the image with the reference exists.
     */
    suspend fun exists(repository: Repository, reference: Reference): Boolean

    /**
     * Retrieve a manifest.
     */
    suspend fun manifest(image: ContainerImageName): Manifest = manifest(image.repository, image.reference)

    /**
     * Retrieve a manifest.
     */
    suspend fun manifest(repository: Repository, reference: Reference): Manifest

    /**
     * Get the digest of the manifest for the provided tag.
     */
    suspend fun manifestDigest(image: ContainerImageName): Digest =
        image.digest ?: manifestDigest(image.repository, image.tag!!)

    /**
     * Get the digest of the manifest for the provided tag.
     */
    suspend fun manifestDigest(repository: Repository, tag: Tag): Digest

    /**
     * Get the config of an Image by its Manifest.
     */
    suspend fun config(repository: Repository, manifest: ManifestSingle): ImageConfig

    /**
     * Get the config of an Image by its reference.
     * This method should only be used, if you know, that the underlying image identified by [reference] is not a
     * ManifestList and is identified uniquely.
     * If the [reference] points to a ManifestList, the behaviour is up to the registry. Usually the first entry of the
     * list is returned.
     *
     * To be safe, it's better to use [config] instead.
     */
    suspend fun config(image: ContainerImageName): ImageConfig = config(image.repository, image.reference)

    /**
     * Get the config of an Image by its reference.
     * This method should only be used, if you know, that the underlying image identified by [reference] is not a
     * ManifestList and is identified uniquely.
     * If the [reference] points to a ManifestList, the behaviour is up to the registry. Usually the first entry of the
     * list is returned.
     *
     * To be safe, it's better to use [config] instead.
     */
    suspend fun config(repository: Repository, reference: Reference): ImageConfig

    /**
     * Retrieve a Blob for an image.
     */
    suspend fun blob(repository: Repository, digest: Digest): ByteArray

    /**
     * Convert general Client to DockerImageClient.
     */
    suspend fun toImageClient(image: ContainerImageName): AsyncContainerImageClient

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(image: ContainerImageName, manifest: ManifestSingle): AsyncContainerImageClient
}

fun AsyncContainerImageRegistryClient.toBlockingClient() = object : ContainerImageRegistryClient {
    override fun testConnection(): Unit = runBlocking { this@toBlockingClient.testConnection() }

    override fun repositories(limit: Int?, last: Int?): List<Repository> =
        runBlocking { this@toBlockingClient.repositories(limit, last) }

    override fun tags(repository: Repository, limit: Int?, last: Int?): List<Tag> =
        runBlocking { this@toBlockingClient.tags(repository, limit, last) }

    override fun exists(repository: Repository, reference: Reference): Boolean =
        runBlocking { this@toBlockingClient.exists(repository, reference) }

    override fun manifest(repository: Repository, reference: Reference): Manifest =
        runBlocking { this@toBlockingClient.manifest(repository, reference) }

    override fun manifestDigest(repository: Repository, tag: Tag): Digest =
        runBlocking { this@toBlockingClient.manifestDigest(repository, tag) }

    override fun config(repository: Repository, manifest: ManifestSingle): ImageConfig =
        runBlocking { this@toBlockingClient.config(repository, manifest) }

    override fun config(repository: Repository, reference: Reference): ImageConfig =
        runBlocking { this@toBlockingClient.config(repository, reference) }

    override fun blob(repository: Repository, digest: Digest): ByteArray =
        runBlocking { this@toBlockingClient.blob(repository, digest) }

    override fun toImageClient(image: ContainerImageName): ContainerImageClient =
        runBlocking { this@toBlockingClient.toImageClient(image).toBlockingClient() }

    override fun toImageClient(image: ContainerImageName, manifest: ManifestSingle): ContainerImageClient =
        this@toBlockingClient.toImageClient(image, manifest).toBlockingClient()
}