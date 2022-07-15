package oci.distribution.client

import com.fasterxml.jackson.core.JsonParseException
import oci.distribution.client.model.domain.Digest
import oci.distribution.client.model.domain.Reference
import oci.distribution.client.model.domain.Repository
import oci.distribution.client.model.domain.Tag
import oci.distribution.client.model.exception.BlobNotFoundException
import oci.distribution.client.model.exception.InvalidResponseException
import oci.distribution.client.model.exception.ManifestNotFoundException
import oci.distribution.client.model.exception.RegistryNotReachableException
import oci.distribution.client.model.exception.RepositoryNotFoundException
import oci.distribution.client.model.exception.UnknownErrorException
import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2
import oci.distribution.client.utils.fold
import java.net.ConnectException

internal class GeneralDistributionClientImpl(private val api: DistributionApi) : GeneralDistributionClient {

    /**
     * Run the given [block] for an api request. Catch all Exceptions and try to map them to client specific ones.
     */
    private fun <T> runThrowingMapping(block: () -> T): T = try {
        block()
    } catch (e: ConnectException) {
        throw RegistryNotReachableException(e)
    } catch (e: JsonParseException) {
        throw InvalidResponseException(e)
    } catch (e: Exception) {
        throw UnknownErrorException(e)
    }

    override fun testConnection(): Result<Unit> {
        val response = runThrowingMapping { api.ping().execute() }

        return when (response.isSuccessful) {
            true -> Result.success(Unit)
            false -> Result.failure(Exception())
        }
    }

    override fun repositories(limit: Int?, last: Int?): Result<List<Repository>> {
        val response = runThrowingMapping { api.images(limit, last).execute() }

        return Result.success(response.body()!!.repositories)
    }

    override fun tags(repository: Repository, limit: Int?, last: Int?): Result<List<Tag>> {
        val response = runThrowingMapping { api.tags(repository, limit, last).execute() }

        return when (response.code()) {
            200 -> Result.success(response.body()!!.tags)
            else -> Result.failure(RepositoryNotFoundException(repository)) // 404 error
        }
    }

    override fun manifest(repository: Repository, reference: Reference): Result<ManifestV2> {
        val response = runThrowingMapping { api.manifest(repository, reference).execute() }

        return when (response.code()) {
            200 -> Result.success(response.body()!!)
            else -> Result.failure(ManifestNotFoundException(repository, reference)) // 404 error
        }
    }

    private fun blob(repository: Repository, digest: Digest): Result<ByteArray> {
        val response = runThrowingMapping { api.blob(repository, digest).execute() }

        return when (response.code()) {
            200 -> Result.success(response.body()!!.bytes())
            else -> Result.failure(BlobNotFoundException(repository, digest)) // 404 error
        }
    }

    override fun imageConfig(repository: Repository, reference: Reference): Result<ImageConfig> {
        return manifest(repository, reference)
            .map { it.config.digest }
            .fold { digest -> blob(repository, digest) }
            .map { mapper.readValue(it, ImageConfig::class.java) }
    }
}
