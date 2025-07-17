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
import de.cmdjulian.kirc.impl.registry.DownloadImageRegistryImpl
import de.cmdjulian.kirc.impl.registry.UploadImageRegistryImpl
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.impl.response.UploadSession
import de.cmdjulian.kirc.spec.image.DockerImageConfigV1
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.image.OciImageConfigV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestV2
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestV1
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlin.math.min

internal class SuspendingContainerImageRegistryClientImpl(private val api: ContainerRegistryApi) :
    SuspendingContainerImageRegistryClient,
    DownloadImageRegistryImpl,
    UploadImageRegistryImpl {

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

    override suspend fun blobStream(repository: Repository, digest: Digest): Source = api.blobStream(repository, digest)
        .getOrElse { throw it.toRegistryClientError(repository) }

    override suspend fun config(repository: Repository, manifestReference: Reference): ImageConfig =
        api.manifest(repository, manifestReference)
            .map { config(repository, it) }
            .getOrElse { throw it.toRegistryClientError(repository, manifestReference) }

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

    override suspend fun initiateBlobUpload(repository: Repository): UploadSession =
        api.initiateUpload(repository).getOrElse { throw it.toRegistryClientError(repository, null) }

    override suspend fun uploadBlobStream(session: UploadSession, stream: Source, size: Long): UploadSession =
        api.uploadBlobStream(session, stream, size).getOrElse { throw it.toRegistryClientError() }

    override suspend fun uploadBlobChunks(session: UploadSession, blob: Source, size: Long): UploadSession {
        var remaining = size
        val buffer = Buffer()
        var returnedSession = session

        blob.use { stream ->
            while (remaining > 0) {
                val readBytes = min(remaining, 10 * 1048576L /* 10 MiB */)
                buffer.write(stream, readBytes)

                val startRange = size - remaining
                val endRange = startRange + readBytes - 1

                returnedSession = api.uploadBlobChunked(returnedSession, buffer, startRange, endRange, readBytes)
                    .getOrElse { throw it.toRegistryClientError(null, null) }

                remaining -= readBytes
            }
        }

        return returnedSession
    }

    override suspend fun finishBlobUpload(session: UploadSession, digest: Digest): Digest =
        api.finishBlobUpload(session, digest)
            .getOrElse { throw it.toRegistryClientError() }

    override suspend fun uploadStatus(session: UploadSession): Pair<Long, Long> =
        api.uploadStatus(session).getOrElse { throw it.toRegistryClientError() }

    override suspend fun cancelBlobUpload(session: UploadSession) {
        api.cancelBlobUpload(session)
            .onError { throw it.toRegistryClientError() }
    }

    override suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: Manifest,
    ): Digest = api.uploadManifest(repository, reference, manifest)
        .getOrElse { throw it.toRegistryClientError(repository, null) }

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
