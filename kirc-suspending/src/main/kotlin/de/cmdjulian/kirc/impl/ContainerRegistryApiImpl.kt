package de.cmdjulian.kirc.impl

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.ResultSource
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.impl.response.UploadSession
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.spec.blob.DockerBlobMediaType
import de.cmdjulian.kirc.spec.blob.OciBlobMediaTypeGzip
import de.cmdjulian.kirc.spec.blob.OciBlobMediaTypeTar
import de.cmdjulian.kirc.spec.blob.OciBlobMediaTypeZstd
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestV2
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import de.cmdjulian.kirc.spec.manifest.OciManifestV1
import de.cmdjulian.kirc.utils.mapSuspending
import de.cmdjulian.kirc.utils.toDigest
import de.cmdjulian.kirc.utils.toRange
import de.cmdjulian.kirc.utils.toResultSource
import de.cmdjulian.kirc.utils.toUploadSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import java.net.URI

private const val APPLICATION_JSON = "application/json"
private const val APPLICATION_OCTET_STREAM = "application/octet-stream"

internal class ContainerRegistryApiImpl(
    private val client: HttpClient,
    private val credentials: RegistryCredentials?,
    private val baseUrl: URI,
) : ContainerRegistryApi {

    private val retriable = ResponseRetryWithAuthentication(credentials, client, baseUrl)

    // Status

    override suspend fun ping(): Result<*, KircApiError> = retriable.execute { client.get("/v2/") }

    override suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, KircApiError> = retriable.execute {
        client.get("/v2/_catalog") {
            if (limit != null) parameter("n", limit)
            if (last != null) parameter("last", last)
            acceptJson()
        }
    }.mapSuspending(HttpResponse::body)

    override suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, KircApiError> =
        retriable.execute {
            client.get("/v2/$repository/tags/list") {
                if (limit != null) parameter("n", limit)
                if (last != null) parameter("last", last)
                acceptJson()
            }
        }.mapSuspending(HttpResponse::body)

    override suspend fun digest(repository: Repository, reference: Reference): Result<Digest, KircApiError> =
        when (reference) {
            is Digest -> Result.success(reference)
            is Tag -> retriable.execute {
                client.head("/v2/$repository/manifests/$reference") { acceptManifestTypes() }
            }.map(HttpResponse::toDigest)
        }

    // Manifest

    override suspend fun existsManifest(
        repository: Repository,
        reference: Reference,
        accept: String,
    ): Result<*, KircApiError> = retriable.execute {
        client.head("/v2/$repository/manifests/$reference") {
            header(HttpHeaders.Accept, accept)
        }
    }

    override suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, KircApiError> =
        retriable.execute {
            client.get("/v2/$repository/manifests/$reference") { acceptManifestTypes() }
        }.mapSuspending(HttpResponse::body)

    override suspend fun manifestStream(
        repository: Repository,
        reference: Reference,
    ): Result<ResultSource, KircApiError> = retriable.execute {
        client.get("/v2/$repository/manifests/$reference") { acceptManifestTypes() }
    }.mapSuspending(HttpResponse::toResultSource)

    override suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, KircApiError> =
        retriable.execute {
            client.get("/v2/$repository/manifests/$reference") {
                acceptSingleManifestTypes()
            }
        }.mapSuspending(HttpResponse::body)

    override suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: Manifest,
    ): Result<Digest, KircApiError> = retriable.execute {
        val urlRef = when (reference) {
            is Digest -> reference.hash
            is Tag -> reference
        }
        val contentType = when (manifest) {
            is DockerManifestV2 -> DockerManifestV2.MediaType
            is DockerManifestListV1 -> DockerManifestListV1.MediaType
            is OciManifestV1 -> OciManifestV1.MediaType
            is OciManifestListV1 -> OciManifestListV1.MediaType
        }
        client.put("/v2/$repository/manifests/$urlRef") {
            header(HttpHeaders.ContentType, contentType)
            setBody(JsonMapper.writeValueAsString(manifest))
        }
    }.map(HttpResponse::toDigest)

    override suspend fun deleteManifest(repository: Repository, reference: Reference): Result<Digest, KircApiError> =
        digest(repository, reference).flatMap { dg ->
            retriable.execute {
                client.delete("/v2/$repository/manifests/$dg") { acceptManifestTypes() }
            }.map { dg }
        }

    // Blob

    override suspend fun existsBlob(repository: Repository, digest: Digest): Result<*, KircApiError> =
        retriable.execute {
            client.head("/v2/$repository/blobs/$digest")
        }

    override suspend fun blob(repository: Repository, digest: Digest): Result<ByteArray, KircApiError> =
        retriable.execute {
            client.get("/v2/$repository/blobs/$digest") { acceptBlobTypes() }
        }.mapSuspending(HttpResponse::body)

    override suspend fun blobStream(repository: Repository, digest: Digest): Result<Source, KircApiError> =
        retriable.execute {
            client.get("/v2/$repository/blobs/$digest") { acceptBlobTypes() }
        }.mapSuspending { resp ->
            resp.bodyAsChannel().toInputStream().asSource().buffered()
        }

    override suspend fun initiateUpload(repository: Repository): Result<UploadSession, KircApiError> =
        retriable.execute {
            client.post("/v2/$repository/blobs/uploads/")
        }.map(HttpResponse::toUploadSession)

    override suspend fun finishBlobUpload(session: UploadSession, digest: Digest): Result<Digest, KircApiError> =
        retriable.execute {
            client.put(session.location) { parameter("digest", digest.toString()) }
        }.map(HttpResponse::toDigest)

    override suspend fun uploadBlobChunked(
        session: UploadSession,
        buffer: Buffer,
        startRange: Long,
        endRange: Long,
    ): Result<UploadSession, KircApiError> = retriable.execute {
        client.patch(session.location) {
            header(HttpHeaders.ContentLength, buffer.size)
            header(HttpHeaders.ContentRange, "$startRange-$endRange")
            header(HttpHeaders.ContentType, APPLICATION_OCTET_STREAM)
            setBody(buffer.readByteArray())
        }
    }.map(HttpResponse::toUploadSession)

    override suspend fun uploadBlobStream(
        session: UploadSession,
        digest: Digest,
        stream: RequestBodyType.Stream,
        size: Long,
    ): Result<Digest, KircApiError> = retriable.execute(stream) {
        client.put(session.location) {
            parameter("digest", digest.toString())
            header(HttpHeaders.ContentType, APPLICATION_OCTET_STREAM)
            header(HttpHeaders.ContentLength, size)
            setBody(stream.channel())
        }
    }.map(HttpResponse::toDigest)

    override suspend fun uploadStatus(session: UploadSession): Result<Pair<Long, Long>, KircApiError> =
        retriable.execute {
            client.get(session.location)
        }.map(HttpResponse::toRange)

    override suspend fun cancelBlobUpload(session: UploadSession): Result<*, KircApiError> = retriable.execute {
        client.delete(session.location)
    }

    private fun HttpRequestBuilder.acceptJson() = header(HttpHeaders.Accept, APPLICATION_JSON)
    private fun HttpRequestBuilder.acceptManifestTypes() = header(
        HttpHeaders.Accept,
        listOf(
            APPLICATION_JSON,
            OciManifestV1.MediaType,
            OciManifestListV1.MediaType,
            DockerManifestV2.MediaType,
            DockerManifestListV1.MediaType,
        ).joinToString(","),
    )

    private fun HttpRequestBuilder.acceptSingleManifestTypes() = header(
        HttpHeaders.Accept,
        listOf(
            APPLICATION_JSON,
            OciManifestV1.MediaType,
            DockerManifestV2.MediaType,
        ).joinToString(","),
    )

    private fun HttpRequestBuilder.acceptBlobTypes() = header(
        HttpHeaders.Accept,
        listOf(
            APPLICATION_JSON,
            APPLICATION_OCTET_STREAM,
            DockerBlobMediaType,
            OciBlobMediaTypeTar,
            OciBlobMediaTypeGzip,
            OciBlobMediaTypeZstd,
        ).joinToString(","),
    )
}
