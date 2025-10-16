package de.cmdjulian.kirc.impl

import com.github.kittinunf.result.getOrElse
import com.github.kittinunf.result.map
import com.github.kittinunf.result.onFailure
import de.cmdjulian.kirc.client.BlobUploadMode
import de.cmdjulian.kirc.client.SuspendingContainerImageClient
import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.delegate.ImageDownloader
import de.cmdjulian.kirc.impl.delegate.ImageUploader
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.ResultSource
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.impl.response.UploadSession
import de.cmdjulian.kirc.impl.serialization.jacksonDeserializer
import de.cmdjulian.kirc.spec.image.DockerImageConfigV1
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.image.OciImageConfigV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestV2
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestV1
import de.cmdjulian.kirc.utils.toRegistryClientError
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asInputStream
import java.nio.file.Path

internal class SuspendingContainerImageRegistryClientImpl(private val api: ContainerRegistryApi, tmpPath: Path) :
    SuspendingContainerImageRegistryClient {

    private val downloader = ImageDownloader(this, tmpPath)
    private val uploader = ImageUploader(this, tmpPath)

    override suspend fun testConnection() {
        api.ping().onFailure {
            throw it.toRegistryClientError()
        }
    }

    override suspend fun existsBlob(repository: Repository, digest: Digest): Boolean =
        api.existsBlob(repository, digest)
            .map { true }
            .getOrElse { error ->
                if (error.statusCode == 404) {
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

    override suspend fun tags(repository: Repository, digest: Digest): List<Tag> {
        val allTags = tags(repository)
        val tagDigests = allTags.associateWith { tag -> manifestDigest(repository, tag) }
        return tagDigests.filterValues { tagDigest -> tagDigest == digest }.keys.toList()
    }

    override suspend fun exists(repository: Repository, reference: Reference): Boolean =
        api.digest(repository, reference)
            .map { true }
            .getOrElse {
                if (it.statusCode == 404) false else throw it.toRegistryClientError(repository, reference)
            }

    override suspend fun manifest(repository: Repository, reference: Reference): Manifest =
        api.manifests(repository, reference)
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun manifestStream(repository: Repository, reference: Reference): ResultSource =
        api.manifestStream(repository, reference)
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun manifestDelete(repository: Repository, reference: Reference): Digest =
        api.deleteManifest(repository, reference)
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun manifestDigest(repository: Repository, reference: Reference): Digest =
        api.digest(repository, reference)
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun blob(repository: Repository, digest: Digest): ByteArray = api.blob(repository, digest)
        .getOrElse { throw it.toRegistryClientError(repository, digest) }

    override suspend fun blobStream(repository: Repository, digest: Digest): Source = api.blobStream(repository, digest)
        .getOrElse { throw it.toRegistryClientError(repository, digest) }

    override suspend fun config(repository: Repository, manifestReference: Reference): ImageConfig =
        api.manifest(repository, manifestReference)
            .map { config(repository, it) }
            .getOrElse { throw it.toRegistryClientError(repository, manifestReference) }

    override suspend fun config(repository: Repository, manifest: ManifestSingle): ImageConfig =
        api.blobStream(repository, manifest.config.digest)
            .map(Source::asInputStream)
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

    override suspend fun uploadBlobStream(session: UploadSession, digest: Digest, path: Path, size: Long): Digest =
        api.uploadBlobStream(session, digest, path, size).getOrElse { throw it.toRegistryClientError() }

    override suspend fun uploadBlobChunks(session: UploadSession, blob: Source, chunkSize: Long): UploadSession =
        blob.use { stream ->
            var returnedSession = session
            var startRange = 0L
            var endRange: Long

            while (!stream.exhausted()) {
                val buffer = Buffer()
                stream.readAtMostTo(buffer, chunkSize)
                endRange = startRange + buffer.size - 1
                returnedSession = api.uploadBlobChunked(returnedSession, buffer, startRange, endRange)
                    .getOrElse { throw it.toRegistryClientError() }
                startRange = endRange
            }

            returnedSession
        }

    override suspend fun finishBlobUpload(session: UploadSession, digest: Digest): Digest =
        api.finishBlobUpload(session, digest)
            .getOrElse { throw it.toRegistryClientError(null, digest) }

    override suspend fun uploadStatus(session: UploadSession): Pair<Long, Long> =
        api.uploadStatus(session).getOrElse { throw it.toRegistryClientError() }

    override suspend fun cancelBlobUpload(session: UploadSession) {
        api.cancelBlobUpload(session).onFailure { throw it.toRegistryClientError() }
    }

    override suspend fun uploadManifest(repository: Repository, reference: Reference, manifest: Manifest): Digest =
        api.uploadManifest(repository, reference, manifest)
            .getOrElse { throw it.toRegistryClientError(repository, reference) }

    override suspend fun upload(
        repository: Repository,
        reference: Reference,
        tar: Source,
        uploadMode: BlobUploadMode,
    ): Digest = uploader.upload(repository, reference, tar, uploadMode)

    override suspend fun download(repository: Repository, reference: Reference): Source =
        downloader.download(repository, reference)

    override suspend fun download(repository: Repository, reference: Reference, destination: Sink) =
        downloader.download(repository, reference, destination)

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
