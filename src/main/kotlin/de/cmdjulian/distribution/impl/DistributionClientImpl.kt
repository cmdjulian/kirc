package de.cmdjulian.distribution.impl

import com.haroldadmin.cnradapter.NetworkResponse
import de.cmdjulian.distribution.DistributionClient
import de.cmdjulian.distribution.ImageClient
import de.cmdjulian.distribution.model.exception.DistributionError
import de.cmdjulian.distribution.model.exception.DistributionError.ClientErrorException.AuthenticationError
import de.cmdjulian.distribution.model.exception.DistributionError.ClientErrorException.AuthorizationError
import de.cmdjulian.distribution.model.exception.DistributionError.ClientErrorException.NotFoundException
import de.cmdjulian.distribution.model.exception.DistributionError.ClientErrorException.UnexpectedClientErrorException
import de.cmdjulian.distribution.model.exception.ErrorResponse
import de.cmdjulian.distribution.model.image.ImageConfig
import de.cmdjulian.distribution.model.manifest.ManifestV2
import de.cmdjulian.distribution.model.oci.Blob
import de.cmdjulian.distribution.model.oci.Digest
import de.cmdjulian.distribution.model.oci.DockerImageSlug
import de.cmdjulian.distribution.model.oci.Reference
import de.cmdjulian.distribution.model.oci.Repository
import de.cmdjulian.distribution.model.oci.Tag
import de.cmdjulian.distribution.utils.foldSuspend
import de.cmdjulian.distribution.utils.getIgnoreCase
import retrofit2.Response

internal class DistributionClientImpl(private val api: DistributionApi) : DistributionClient {

    @JvmName("toResultIgnoreError")
    private fun <S, T> NetworkResponse<S, Unit>.toResult(block: (S, Response<*>) -> T): Result<T> {
        return when (this) {
            is NetworkResponse.Success -> Result.success(block(body, response))
            is NetworkResponse.NetworkError -> Result.failure(DistributionError.NetworkError(error))
            is NetworkResponse.UnknownError -> Result.failure(DistributionError.UnknownError(error))
            is NetworkResponse.ServerError -> when (code!!) {
                401 -> Result.failure(AuthenticationError(null))
                403 -> Result.failure(AuthorizationError(null))
                404 -> Result.failure(NotFoundException(null))
                else -> {
                    println(code)
                    Result.failure(UnexpectedClientErrorException(null))
                }
            }
        }
    }

    @JvmName("toResultIgnoreError")
    private fun <S> NetworkResponse<S, Unit>.toResult(): Result<S> = toResult { body, _ -> body }

    @JvmName("toResult")
    private fun <S, U> NetworkResponse<S, ErrorResponse>.toResult(block: (S, Response<*>) -> U): Result<U> {
        return when (this) {
            is NetworkResponse.Success -> runCatching { block(body, response) }
            is NetworkResponse.NetworkError -> Result.failure(DistributionError.NetworkError(error))
            is NetworkResponse.UnknownError -> Result.failure(DistributionError.UnknownError(error))
            is NetworkResponse.ServerError -> when (code!!) {
                401 -> Result.failure(AuthenticationError(body?.errors?.firstOrNull()))
                403 -> Result.failure(AuthorizationError(body?.errors?.firstOrNull()))
                404 -> Result.failure(NotFoundException(body?.errors?.firstOrNull()))
                else -> Result.failure(UnexpectedClientErrorException(body?.errors?.firstOrNull()))
            }
        }
    }

    @JvmName("toResult")
    private fun <S> NetworkResponse<S, ErrorResponse>.toResult(): Result<S> = toResult { s, _ -> s }

    override suspend fun testConnection(): Boolean {
        return api.ping().toResult().isSuccess
    }

    override suspend fun repositories(limit: Int?, last: Int?): Result<List<Repository>> {
        return api.images(limit, last).toResult { catalog, _ -> catalog.repositories }
    }

    override suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<List<Tag>> {
        return api.tags(repository, limit, last).toResult { tagList, _ -> tagList.tags }
    }

    override suspend fun exists(repository: Repository, reference: Reference): Result<Boolean> {
        return manifest(repository, reference)
            .map { true }
            .recoverCatching { throwable -> if (throwable is NotFoundException) false else throw throwable }
    }

    override suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestV2> {
        return api.manifest(repository, reference).toResult()
    }

    override suspend fun manifestDigest(repository: Repository, tag: Tag): Result<Digest> {
        return api.manifest(repository, tag).toResult { _, response ->
            Digest(response.headers().getIgnoreCase("Docker-content-digest")!!)
        }
    }

    override suspend fun deleteManifest(repository: Repository, reference: Reference): Result<*> {
        return when (reference) {
            is Digest -> api.deleteManifest(repository, reference).toResult()
            is Tag -> manifestDigest(repository, reference).foldSuspend { digest -> deleteManifest(repository, digest) }
        }
    }

    override suspend fun blob(repository: Repository, digest: Digest): Result<Blob> {
        return api.blob(repository, digest).toResult { response, _ ->
            Blob(digest, "application/vnd.docker.image.rootfs.diff.tar.gzip", response.bytes())
        }
    }

    override suspend fun deleteBlob(repository: Repository, digest: Digest): Result<*> {
        return api.deleteBlob(repository, digest).toResult()
    }

    internal suspend fun config(repository: Repository, digest: Digest): Result<ImageConfig> {
        return blob(repository, digest)
            .map(Blob::data)
            .map { bytes -> jsonMapper.readValue(bytes, ImageConfig::class.java) }
    }

    override suspend fun config(repository: Repository, reference: Reference): Result<ImageConfig> {
        return manifest(repository, reference)
            .map { manifest -> manifest.config.digest }
            .foldSuspend { digest -> config(repository, digest) }
    }

    override fun toImageClient(repository: Repository, reference: Reference?): ImageClient =
        when (reference) {
            null -> ImageClientImpl(this, DockerImageSlug(repository = repository))
            is Tag -> ImageClientImpl(this, DockerImageSlug(repository = repository, tag = reference))
            is Digest -> ImageClientImpl(this, DockerImageSlug(repository = repository, digest = reference))
        }
}
