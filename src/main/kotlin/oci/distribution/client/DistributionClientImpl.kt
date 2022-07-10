package oci.distribution.client

import oci.distribution.client.model.domain.Reference
import oci.distribution.client.model.domain.Repository
import oci.distribution.client.model.domain.Tag
import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2

internal class DistributionClientImpl(private val api: DistributionApi) : DistributionClient {
    override fun testConnection(): Result<Unit> {
        val response = api.ping()

        return when (response.isSuccessful) {
            true -> Result.success(Unit)
            false -> Result.failure(Exception())
        }
    }

    override fun repositories(limit: Int?, last: Int?): List<Repository> {
        return api.images(limit, last).body()!!.repositories
    }

    override fun tags(repository: Repository, limit: Int?, last: Int?): List<Tag> {
        return api.tags(repository, limit, last).body()!!.tags.map(::Tag)
    }

    override fun manifest(repository: Repository, reference: Reference): ManifestV2 {
        return api.manifest(repository, reference).body()!!
    }

    override fun imageConfig(repository: Repository, reference: Reference): ImageConfig {
        TODO("Not yet implemented")
    }
}
