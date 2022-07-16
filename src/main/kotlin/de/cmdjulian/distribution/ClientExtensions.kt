package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.oci.Digest
import de.cmdjulian.distribution.model.oci.Reference
import de.cmdjulian.distribution.model.oci.Repository
import de.cmdjulian.distribution.model.oci.Tag
import kotlinx.coroutines.runBlocking

fun DistributionClient.toBlockingClient() = object : BlockingDistributionClient {
    override fun testConnection() = runBlocking { this@toBlockingClient.testConnection() }

    override fun repositories(limit: Int?, last: Int?) = runBlocking { this@toBlockingClient.repositories(limit, last) }

    override fun tags(repository: Repository, limit: Int?, last: Int?) =
        runBlocking { this@toBlockingClient.tags(repository, limit, last) }

    override fun exists(repository: Repository, reference: Reference) =
        runBlocking { this@toBlockingClient.exists(repository, reference) }

    override fun manifest(repository: Repository, reference: Reference) =
        runBlocking { this@toBlockingClient.manifest(repository, reference) }

    override fun manifestDigest(repository: Repository, tag: Tag) =
        runBlocking { this@toBlockingClient.manifestDigest(repository, tag) }

    override fun deleteManifest(repository: Repository, reference: Reference) =
        runBlocking { this@toBlockingClient.deleteManifest(repository, reference) }

    override fun config(repository: Repository, reference: Reference) =
        runBlocking { this@toBlockingClient.config(repository, reference) }

    override fun blob(repository: Repository, digest: Digest) =
        runBlocking { this@toBlockingClient.blob(repository, digest) }

    override fun deleteBlob(repository: Repository, digest: Digest) =
        runBlocking { this@toBlockingClient.deleteBlob(repository, digest) }

    override fun toImageClient(
        repository: Repository,
        reference: Reference?
    ): BlockingDockerImageClient =
        this@toBlockingClient.toImageClient(repository, reference).toBlockingClient()
}

fun DockerImageClient.toBlockingClient() = object : BlockingDockerImageClient {
    override fun exists() = runBlocking { this@toBlockingClient.exists() }

    override fun tags(limit: Int?, last: Int?) = runBlocking { this@toBlockingClient.tags(limit, last) }

    override fun manifest() = runBlocking { this@toBlockingClient.manifest() }

    override fun config() = runBlocking { this@toBlockingClient.config() }

    override fun blobs() = runBlocking { this@toBlockingClient.blobs() }

    override fun delete() = runBlocking { this@toBlockingClient.delete() }

    override fun size() = runBlocking { this@toBlockingClient.size() }

    override fun toDockerImage() = runBlocking { this@toBlockingClient.toDockerImage() }
}
