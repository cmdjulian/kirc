package oci.distribution.client

import oci.distribution.client.model.domain.Digest
import oci.distribution.client.model.domain.Reference
import oci.distribution.client.model.domain.Repository
import oci.distribution.client.model.domain.Tag
import oci.distribution.client.model.exception.RegistryNotReachableException
import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2
import java.net.ConnectException

internal class DistributionClientImpl(private val api: DistributionApi) : DistributionClient {
    override fun testConnection(): Result<Unit> {
        val response = try {
            api.ping().execute()
        } catch (e: ConnectException) {
            return Result.failure(RegistryNotReachableException(e))
        }

        return when (response.isSuccessful) {
            true -> Result.success(Unit)
            false -> Result.failure(Exception())
        }
    }

    override fun repositories(limit: Int?, last: Int?): Result<List<Repository>> {
        val response = try {
            api.images(limit, last).execute()
        } catch (e: ConnectException) {
            return Result.failure(RegistryNotReachableException(e))
        }

        return Result.success(response.body()!!.repositories)
    }

    override fun tags(repository: Repository, limit: Int?, last: Int?): Result<List<Tag>> {
        val response = try {
            api.tags(repository, limit, last).execute()
        } catch (e: ConnectException) {
            return Result.failure(RegistryNotReachableException(e))
        }

        return Result.success(response.body()!!.tags)
    }

    override fun manifest(repository: Repository, reference: Reference): Result<ManifestV2> {
        val response = try {
            api.manifest(repository, reference).execute()
        } catch (e: ConnectException) {
            return Result.failure(RegistryNotReachableException(e))
        }

        return Result.success(response.body()!!)
    }

    private fun blob(repository: Repository, digest: Digest): Result<ByteArray> {
        val response = try {
            api.blob(repository, digest).execute()
        } catch (e: ConnectException) {
            return Result.failure(RegistryNotReachableException(e))
        }

        return Result.success(response.body()!!.bytes())
    }

    override fun imageConfig(repository: Repository, reference: Reference): Result<ImageConfig> {
        return manifest(repository, reference)
            .map { it.config.digest }
            .mapCatching { digest -> blob(repository, digest).getOrThrow() }
            .map { println(String(it)); mapper.readValue(it, ImageConfig::class.java) }
    }
}
