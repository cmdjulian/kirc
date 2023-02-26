package de.cmdjulian.distribution.impl

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.getOrElse
import com.github.kittinunf.result.map
import com.github.kittinunf.result.onError
import de.cmdjulian.distribution.AsyncContainerImageClient
import de.cmdjulian.distribution.AsyncContainerImageRegistryClient
import de.cmdjulian.distribution.exception.ErrorResponse
import de.cmdjulian.distribution.exception.RegistryClientException
import de.cmdjulian.distribution.exception.RegistryClientException.ClientException.AuthenticationException
import de.cmdjulian.distribution.exception.RegistryClientException.ClientException.AuthorizationException
import de.cmdjulian.distribution.exception.RegistryClientException.ClientException.NotFoundException
import de.cmdjulian.distribution.exception.RegistryClientException.ClientException.UnexpectedErrorException
import de.cmdjulian.distribution.exception.RegistryClientException.NetworkErrorException
import de.cmdjulian.distribution.exception.RegistryClientException.UnknownErrorException
import de.cmdjulian.distribution.impl.response.Catalog
import de.cmdjulian.distribution.impl.response.TagList
import de.cmdjulian.distribution.model.ContainerImageName
import de.cmdjulian.distribution.model.Digest
import de.cmdjulian.distribution.model.Reference
import de.cmdjulian.distribution.model.Repository
import de.cmdjulian.distribution.model.Tag
import de.cmdjulian.distribution.spec.image.DockerImageConfigV1
import de.cmdjulian.distribution.spec.image.ImageConfig
import de.cmdjulian.distribution.spec.image.OciImageConfigV1
import de.cmdjulian.distribution.spec.manifest.DockerManifestV2
import de.cmdjulian.distribution.spec.manifest.Manifest
import de.cmdjulian.distribution.spec.manifest.ManifestSingle
import de.cmdjulian.distribution.spec.manifest.OciManifestV1

@Suppress("TooManyFunctions")
internal class AsyncContainerImageRegistryClientImpl(private val api: ContainerRegistryApi) :
    AsyncContainerImageRegistryClient {

    override suspend fun testConnection() {
        api.ping().onError { throw it.toRegistryClientError() }
    }

    override suspend fun repositories(limit: Int?, last: Int?): List<Repository> =
        api.repositories(limit, last)
            .map(Catalog::repositories)
            .getOrElse { throw it.toRegistryClientError() }

    override suspend fun tags(repository: Repository, limit: Int?, last: Int?): List<Tag> =
        api.tags(repository, limit, last)
            .map(TagList::tags)
            .getOrElse { throw it.toRegistryClientError() }

    override suspend fun exists(repository: Repository, reference: Reference): Boolean =
        api.digest(repository, reference)
            .map { true }
            .getOrElse { if (it.response.statusCode == 404) false else throw it.toRegistryClientError() }

    override suspend fun manifest(repository: Repository, reference: Reference): Manifest =
        api.manifests(repository, reference).getOrElse { throw it.toRegistryClientError() }

    override suspend fun manifestDigest(repository: Repository, tag: Tag): Digest =
        api.digest(repository, tag).getOrElse { throw it.toRegistryClientError() }

    override suspend fun blob(repository: Repository, digest: Digest): ByteArray =
        api.blob(repository, digest).getOrElse { throw it.toRegistryClientError() }

    override suspend fun config(repository: Repository, reference: Reference): ImageConfig =
        api.manifest(repository, reference)
            .map { config(repository, it) }
            .getOrElse { throw it.toRegistryClientError() }

    override suspend fun config(repository: Repository, manifest: ManifestSingle): ImageConfig =
        api.blob(repository, manifest.config.digest)
            .map { config ->
                when (manifest) {
                    is DockerManifestV2 -> JsonMapper.readValue<DockerImageConfigV1>(config)
                    is OciManifestV1 -> JsonMapper.readValue<OciImageConfigV1>(config)
                }
            }
            .getOrElse { throw it.toRegistryClientError() }

    override suspend fun toImageClient(image: ContainerImageName): AsyncContainerImageClient =
        AsyncContainerImageClientImpl(this, image)

    override fun toImageClient(image: ContainerImageName, manifest: ManifestSingle): AsyncContainerImageClient =
        AsyncContainerImageClientImpl(this, image, manifest)
}

private fun FuelError.toRegistryClientError(): RegistryClientException = when (response.statusCode) {
    -1 -> NetworkErrorException(this)
    401 -> AuthenticationException(runCatching { JsonMapper.readValue<ErrorResponse>(response.data) }.getOrNull(), this)
    403 -> AuthorizationException(runCatching { JsonMapper.readValue<ErrorResponse>(response.data) }.getOrNull(), this)
    404 -> NotFoundException(runCatching { JsonMapper.readValue<ErrorResponse>(response.data) }.getOrNull(), this)
    in 405..499 ->
        UnexpectedErrorException(runCatching { JsonMapper.readValue<ErrorResponse>(response.data) }.getOrNull(), this)

    else -> UnknownErrorException(this)
}
