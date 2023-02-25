package de.cmdjulian.distribution.impl

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.result.getOrElse
import com.github.kittinunf.result.map
import com.github.kittinunf.result.onError
import de.cmdjulian.distribution.CoroutineContainerRegistryClient
import de.cmdjulian.distribution.CoroutineImageClient
import de.cmdjulian.distribution.exception.RegistryClientException
import de.cmdjulian.distribution.exception.RegistryClientException.ClientException.AuthenticationException
import de.cmdjulian.distribution.exception.RegistryClientException.ClientException.AuthorizationException
import de.cmdjulian.distribution.exception.RegistryClientException.ClientException.NotFoundException
import de.cmdjulian.distribution.exception.RegistryClientException.ClientException.UnexpectedErrorException
import de.cmdjulian.distribution.exception.RegistryClientException.NetworkErrorException
import de.cmdjulian.distribution.exception.RegistryClientException.UnknownErrorException
import de.cmdjulian.distribution.impl.response.Catalog
import de.cmdjulian.distribution.impl.response.TagList
import de.cmdjulian.distribution.model.Blob
import de.cmdjulian.distribution.model.Digest
import de.cmdjulian.distribution.model.ContainerImageName
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
import de.cmdjulian.distribution.utils.CaseInsensitiveMap

@Suppress("TooManyFunctions")
internal class CoroutineContainerRegistryClientImpl(private val api: ContainerRegistryApi) :
    CoroutineContainerRegistryClient {

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
        api.manifest(repository, reference)
            .map { true }
            .getOrElse { if (it.response.statusCode == 404) false else throw it.toRegistryClientError() }

    override suspend fun manifest(repository: Repository, reference: Reference): Manifest =
        api.manifests(repository, reference)
            .getOrElse { throw it.toRegistryClientError() }

    override suspend fun manifestDigest(repository: Repository, tag: Tag): Digest =
        api.digest(repository, tag)
            .getOrElse { throw it.toRegistryClientError() }

    override suspend fun blob(repository: Repository, digest: Digest): Blob {
        val (_, response, result) = api.blob(repository, digest)

        val headers = CaseInsensitiveMap(response.headers)
        // TODO
        return result.map { content -> Blob(digest, headers[Headers.CONTENT_TYPE]?.single()!!, content) }
            .getOrElse { throw it.toRegistryClientError() }
    }

    override suspend fun config(repository: Repository, reference: Reference): ImageConfig =
        api.manifest(repository, reference)
            .map { config(repository, it) }
            .getOrElse { throw it.toRegistryClientError() }

    override suspend fun config(repository: Repository, manifest: ManifestSingle): ImageConfig =
        api.blob(repository, manifest.config.digest)
            .third
            .map { config ->
                when (manifest) {
                    is DockerManifestV2 -> JsonMapper.readValue<DockerImageConfigV1>(config)
                    is OciManifestV1 -> JsonMapper.readValue<OciImageConfigV1>(config)
                }
            }
            .getOrElse { throw it.toRegistryClientError() }

    override fun toImageClient(image: ContainerImageName, manifest: ManifestSingle): CoroutineImageClient =
        CoroutineImageClientImpl(this, image, manifest)
}

private fun FuelError.toRegistryClientError(): RegistryClientException = when (response.statusCode) {
    -1 -> NetworkErrorException(this)
    401 -> AuthenticationException(JsonMapper.readValue(response.data), this)
    403 -> AuthorizationException(JsonMapper.readValue(response.data), this)
    404 -> NotFoundException(JsonMapper.readValue(response.data), this)
    in 405..499 ->
        UnexpectedErrorException(if (response.data.isEmpty()) null else JsonMapper.readValue(response.data), this)

    else -> UnknownErrorException(this)
}
