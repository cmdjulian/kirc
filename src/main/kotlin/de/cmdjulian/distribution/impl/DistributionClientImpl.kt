package de.cmdjulian.distribution.impl

import com.fasterxml.jackson.module.kotlin.readValue
import de.cmdjulian.distribution.DistributionClient
import de.cmdjulian.distribution.ImageClient
import de.cmdjulian.distribution.model.exception.DistributionError.ClientErrorException.NotFoundException
import de.cmdjulian.distribution.model.image.ImageConfigV1
import de.cmdjulian.distribution.model.manifest.docker.ManifestV2
import de.cmdjulian.distribution.model.oci.Blob
import de.cmdjulian.distribution.model.oci.Digest
import de.cmdjulian.distribution.model.oci.DockerImageSlug
import de.cmdjulian.distribution.model.oci.Reference
import de.cmdjulian.distribution.model.oci.Repository
import de.cmdjulian.distribution.model.oci.Tag
import de.cmdjulian.distribution.utils.foldSuspend
import de.cmdjulian.distribution.utils.getIgnoreCase

@Suppress("TooManyFunctions")
internal class DistributionClientImpl(private val api: DistributionApi) : DistributionClient {

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
        fun recoverNotFound(throwable: Throwable) = when (throwable) {
            is NotFoundException -> false
            else -> throw throwable
        }

        return manifest(repository, reference)
            .map { true }
            .recoverCatching(::recoverNotFound)
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

    override suspend fun config(repository: Repository, reference: Reference): Result<ImageConfigV1> {
        return manifest(repository, reference)
            .map { manifest -> manifest.config.digest }
            .foldSuspend { digest -> config(repository, digest) }
    }

    internal suspend fun config(repository: Repository, digest: Digest): Result<ImageConfigV1> {
        return blob(repository, digest)
            .map(Blob::data)
            .map { bytes -> JsonMapper.readValue(bytes) }
    }

    override fun toImageClient(repository: Repository, reference: Reference?): ImageClient =
        when (reference) {
            null -> ImageClientImpl(this, DockerImageSlug(repository = repository))
            is Tag -> ImageClientImpl(this, DockerImageSlug(repository = repository, tag = reference))
            is Digest -> ImageClientImpl(this, DockerImageSlug(repository = repository, digest = reference))
        }
}
