package de.cmdjulian.kirc.impl

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.getOrElse
import com.github.kittinunf.result.map
import com.github.kittinunf.result.onError
import de.cmdjulian.kirc.client.SuspendingContainerImageClient
import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.exception.RegistryClientException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.AuthenticationException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.AuthorizationException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.MethodNotAllowed
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.NotFoundException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.UnexpectedErrorException
import de.cmdjulian.kirc.exception.RegistryClientException.NetworkErrorException
import de.cmdjulian.kirc.exception.RegistryClientException.UnknownErrorException
import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.spec.DownloadImage
import de.cmdjulian.kirc.spec.Platform
import de.cmdjulian.kirc.spec.image.DockerImageConfigV1
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.image.OciImageConfigV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestV2
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import de.cmdjulian.kirc.spec.manifest.OciManifestV1
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.anarres.parallelgzip.ParallelGZIPInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

internal class SuspendingContainerImageRegistryClientImpl(private val api: ContainerRegistryApi) :
    SuspendingContainerImageRegistryClient {

    override suspend fun testConnection() {
        api.ping().onError {
            throw it.toRegistryClientError()
        }
    }

    override suspend fun existsBlob(repository: Repository, digest: Digest): Boolean =
        api.existsBlob(repository, digest)
            .map { true }
            .getOrElse { error ->
                if (error.response.statusCode == 404) {
                    false
                } else {
                    throw error.toRegistryClientError(repository, digest)
                }
            }

    override suspend fun repositories(limit: Int?, last: Int?): List<Repository> = api.repositories(limit, last)
        .map(Catalog::repositories)
        .getOrElse { throw it.toRegistryClientError() }

    override suspend fun tags(repository: Repository, limit: Int?, last: Int?): List<Tag> =
        api.tags(repository, limit, last)
            .map(TagList::tags)
            .getOrElse { throw it.toRegistryClientError(repository, null) }

    override suspend fun exists(repository: Repository, reference: Reference): Boolean =
        api.digest(repository, reference)
            .map { true }
            .getOrElse {
                if (it.response.statusCode == 404) false else throw it.toRegistryClientError(repository, reference)
            }

    override suspend fun manifest(repository: Repository, reference: Reference): Manifest =
        api.manifests(repository, reference)
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun manifestDelete(repository: Repository, reference: Reference): Digest =
        api.deleteManifest(repository, reference)
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun manifestDigest(repository: Repository, reference: Reference): Digest =
        api.digest(repository, reference)
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun blob(repository: Repository, digest: Digest): ByteArray = api.blob(repository, digest)
        .getOrElse { throw it.toRegistryClientError(repository) }

    override suspend fun config(repository: Repository, reference: Reference): ImageConfig =
        api.manifest(repository, reference)
            .map { config(repository, it) }
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun config(repository: Repository, manifest: ManifestSingle): ImageConfig =
        api.blob(repository, manifest.config.digest)
            .map { config ->
                when (manifest) {
                    is DockerManifestV2 -> jacksonDeserializer<DockerImageConfigV1>().deserialize(config)
                    is OciManifestV1 -> jacksonDeserializer<OciImageConfigV1>().deserialize(config)
                }
            }
            .getOrElse { throw it.toRegistryClientError(repository) }

    // Upload

    override suspend fun initiateUpload(repository: Repository, from: Repository?, mount: Digest?): UUID? =
        api.initiateUpload(repository, from, mount).getOrElse { throw it.toRegistryClientError(repository, null) }

    override suspend fun uploadBlob(
        repository: Repository,
        uploadUUID: UUID,
        digest: Digest,
        blob: ByteArray?,
    ): Digest = api.uploadBlob(repository, uploadUUID, digest, blob)
        .getOrElse { throw it.toRegistryClientError(repository, null) }

    override suspend fun cancelBlobUpload(repository: Repository, sessionUUID: UUID) {
        api.cancelBlobUpload(repository, sessionUUID)
            .onError { throw it.toRegistryClientError(repository, null) }
    }

    override suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle,
    ): Digest = api.uploadManifest(repository, reference, manifest)
        .getOrElse { throw it.toRegistryClientError(repository, null) }

    override suspend fun upload(
        repository: Repository,
        reference: Reference,
        gzip: InputStream,
    ) {
        // first decompress & deserialize
        val uploadImages = gzip.use(::ParallelGZIPInputStream).use { data ->
            OciImageArchiveProcessor.readFromGZIP(data)
        }

        // upload architecture images
        uploadImages.forEach { (manifest, digest, blobs) ->
            blobs.forEach { blob ->
                if (!existsBlob(repository, blob.digest)) {
                    val sessionUUID = initiateUpload(repository, null, null)
                        ?: error("No session id returned from registry upon initiating upload for repository '$repository'")
                    uploadBlob(repository, sessionUUID, blob.digest, blob.data)
                }
                uploadManifest(repository, digest, manifest)
            }
        }
    }

    // Download

    override suspend fun download(
        repository: Repository,
        reference: Reference,
        platform: Platform?,
    ): suspend (OutputStream) -> Unit =
        when (val index = manifest(repository, reference)) {
            is ManifestSingle -> error(
                "Expected a reference to an index manifest but received a reference to an image manifest " +
                    "'$repository/${reference.asImagePart()}'",
            )

            is ManifestList -> {
                val images = index.manifests.mapNotNull { listEntry ->
                    val singleManifest = manifest(repository, listEntry.digest) as ManifestSingle
                    singleManifest.toDownloadImage(repository, listEntry.digest, platform)
                }
                // we need to filter the index, so that it contains no manifests of another platform, other than specified
                val filteredIndex = if (platform == null) {
                    index
                } else {
                    index.filterForDigests(images.map(DownloadImage::digest))
                }
                OciImageArchiveProcessor.writeToGZIP(filteredIndex, images)
            }
        }

    private fun ManifestList.filterForDigests(digests: List<Digest>): ManifestList {
        fun filterManifests() = manifests.filter { digests.contains(it.digest) }
        return when (this) {
            is DockerManifestListV1 -> copy(manifests = filterManifests())
            is OciManifestListV1 -> copy(manifests = filterManifests())
        }
    }

    private suspend fun getBlobWithCheck(repository: Repository, digest: Digest): ByteArray {
        if (!existsBlob(repository, digest)) {
            error("Blob does not exist!")
        }
        return blob(repository, digest)
    }

    private suspend fun ManifestSingle.toDownloadImage(
        repository: Repository,
        manifestDigest: Digest,
        platform: Platform?,
    ): DownloadImage? =
        coroutineScope {
            val config = getBlobWithCheck(repository, this@toDownloadImage.config.digest).let {
                jacksonDeserializer<ImageConfig>().deserialize(it)
            }
            val deferredLayerBlobs = layers.map { layer ->
                DownloadImage.DeferredImageBlob(
                    layer.digest,
                    async { getBlobWithCheck(repository, layer.digest) },
                )
            }

            // filter image for platform
            if (platform != null && platform.os == config.os && platform.arch == config.architecture) {
                DownloadImage(this@toDownloadImage, manifestDigest, config, deferredLayerBlobs)
            } else {
                null
            }
        }

    // To Image Client

    override suspend fun toImageClient(repository: Repository, reference: Reference): SuspendingContainerImageClient =
        when (reference) {
            is Tag -> SuspendingContainerImageClientImpl(
                this,
                ContainerImageName(repository = repository, tag = reference),
            )

            is Digest -> SuspendingContainerImageClientImpl(
                this,
                ContainerImageName(repository = repository, digest = reference),
            )
        }

    override fun toImageClient(repository: Repository, reference: Reference, manifest: ManifestSingle) =
        when (reference) {
            is Tag -> SuspendingContainerImageClientImpl(
                this,
                ContainerImageName(repository = repository, tag = reference),
                manifest,
            )

            is Digest -> SuspendingContainerImageClientImpl(
                this,
                ContainerImageName(repository = repository, digest = reference),
                manifest,
            )
        }
}

private fun FuelError.toRegistryClientError(
    repository: Repository? = null,
    reference: Reference? = null,
): RegistryClientException {
    val url = this.response.url
    val data = response.data
    return when (response.statusCode) {
        // todo add missing statusCodes
        -1 -> NetworkErrorException(url, repository, reference, this)

        401 -> AuthenticationException(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, this)

        403 -> AuthorizationException(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, this)

        404 -> NotFoundException(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, this)

        405 -> MethodNotAllowed(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, this)
        in 406..499 ->
            UnexpectedErrorException(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, this)

        else -> UnknownErrorException(url, repository, reference, this)
    }
}

private inline fun <T> tryOrNull(block: () -> T): T? = runCatching(block).getOrNull()
