package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle

interface SuspendingContainerImageRegistryClient {
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
    suspend fun toImageClient(image: ContainerImageName): SuspendingContainerImageClient

    /**
     * Convert general Client to DockerImageClient.
     */
    fun toImageClient(image: ContainerImageName, manifest: ManifestSingle): SuspendingContainerImageClient
}
