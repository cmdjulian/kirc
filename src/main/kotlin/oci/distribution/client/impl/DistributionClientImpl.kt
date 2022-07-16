package oci.distribution.client.impl

import com.haroldadmin.cnradapter.NetworkResponse
import oci.distribution.client.api.DistributionApi
import oci.distribution.client.api.DistributionClient
import oci.distribution.client.api.mapper
import oci.distribution.client.model.exception.DistributionError
import oci.distribution.client.model.exception.DistributionError.ClientErrorException.AuthenticationError
import oci.distribution.client.model.exception.DistributionError.ClientErrorException.AuthorizationError
import oci.distribution.client.model.exception.DistributionError.ClientErrorException.NotFoundException
import oci.distribution.client.model.exception.DistributionError.ClientErrorException.UnexpectedClientErrorException
import oci.distribution.client.model.exception.ErrorResponse
import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2
import oci.distribution.client.model.oci.Digest
import oci.distribution.client.model.oci.Reference
import oci.distribution.client.model.oci.Repository
import oci.distribution.client.model.oci.Tag
import oci.distribution.client.utils.foldSuspend
import oci.distribution.client.utils.getIgnoreCase
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

    override suspend fun blob(repository: Repository, digest: Digest): Result<Pair<String, ByteArray>> {
        return api.blob(repository, digest).toResult { response, res ->
            res.headers()["Content-Type"]!! to response.bytes()
        }
    }

    override suspend fun deleteBlob(repository: Repository, digest: Digest): Result<*> {
        return api.deleteBlob(repository, digest).toResult()
    }

    override suspend fun config(repository: Repository, reference: Reference): Result<ImageConfig> {
        return manifest(repository, reference)
            .map { manifest -> manifest.config.digest }
            .foldSuspend { digest -> blob(repository, digest) }
            .map { (_, content) -> content }
            .map { bytes -> mapper.readValue(bytes, ImageConfig::class.java) }
    }
}
