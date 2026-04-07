@file:OptIn(InternalKircApi::class)

package de.cmdjulian.kirc.impl

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import de.cmdjulian.kirc.annotation.InternalKircApi
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.auth.AuthAttributes
import de.cmdjulian.kirc.impl.auth.ScopeType
import de.cmdjulian.kirc.impl.auth.currentSession
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.ResultSource
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.impl.response.UploadSession
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.spec.RegistryErrorResponse
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
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import java.nio.file.Path
import java.util.*

private const val APPLICATION_JSON = "application/json"
private const val APPLICATION_OCTET_STREAM = "application/octet-stream"

internal class ContainerRegistryApiImpl(private val client: HttpClient) : ContainerRegistryApi {

    /**
     * Executes the given [block] and maps error codes to [KircApiError].
     *
     * At best, [block] doesn't throw any exception, but if, it is caught as [KircApiError.Unknown].
     * Exceptions thrown during bearer auth are also [KircApiError] and are not wrapped.
     */
    private suspend inline fun <T> execute(
        crossinline transform: suspend (HttpResponse) -> T,
        block: suspend () -> HttpResponse,
    ): Result<T, KircApiError> = try {
        val result = block()
        if (result.status.isSuccess()) {
            Result.success(transform(result))
        } else {
            Result.failure(result.toErrorResponse())
        }
    } catch (e: Exception) {
        when (e) {
            is KircApiError -> Result.failure(e)
            else -> Result.failure(KircApiError.Unknown(e))
        }
    }

    private fun HttpStatusCode.isSuccess() = value in 200..299

    // Tries to parse the error response body, otherwise returns a generic JSON error.
    private suspend fun HttpResponse.toErrorResponse() = try {
        KircApiError.Registry(
            statusCode = status.value,
            url = request.url,
            method = request.method,
            body = body<RegistryErrorResponse>(),
        )
    } catch (e: Exception) {
        KircApiError.Json(
            statusCode = status.value,
            url = request.url,
            method = request.method,
            cause = e,
            message = "Could not parse registry error response body",
        )
    }

    // Status

    override suspend fun ping(): Result<*, KircApiError> = execute({}) {
        client.get("/v2/") {
            setAuthSession(currentSession())
        }
    }

    override suspend fun authChallenge(repository: Repository, type: ScopeType): Result<*, KircApiError> = execute({}) {
        client.get("/v2/") {
            val session = currentSession()
            setAttributes {
                put(AuthAttributes.SESSION_ID, session.toString())
                put(AuthAttributes.SKIP_REFRESH, true)
                put(AuthAttributes.SCOPE_REPO, repository.toString())
                put(AuthAttributes.SCOPE_TYPE, type.value)
            }
        }
    }

    override suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, KircApiError> =
        execute(HttpResponse::body) {
            client.get("/v2/_catalog") {
                if (limit != null) parameter("n", limit)
                if (last != null) parameter("last", last)
                acceptJson()
                setAuthSession(currentSession())
            }
        }

    override suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, KircApiError> =
        execute(HttpResponse::body) {
            client.get("/v2/$repository/tags/list") {
                if (limit != null) parameter("n", limit)
                if (last != null) parameter("last", last)
                acceptJson()
                setAuthSession(currentSession())
            }
        }

    override suspend fun digest(repository: Repository, reference: Reference): Result<Digest, KircApiError> =
        when (reference) {
            is Digest -> Result.success(reference)

            is Tag -> execute(HttpResponse::toDigest) {
                client.head("/v2/$repository/manifests/$reference") {
                    acceptManifestTypes()
                    setAuthSession(currentSession())
                }
            }
        }

    // Manifest

    override suspend fun existsManifest(
        repository: Repository,
        reference: Reference,
        accept: String,
    ): Result<*, KircApiError> = execute({}) {
        client.head("/v2/$repository/manifests/$reference") {
            header(HttpHeaders.Accept, accept)
            setAuthSession(currentSession())
        }
    }

    override suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, KircApiError> =
        execute(HttpResponse::body) {
            client.get("/v2/$repository/manifests/$reference") {
                acceptManifestTypes()
                setAuthSession(currentSession())
            }
        }

    override suspend fun manifestStream(
        repository: Repository,
        reference: Reference,
    ): Result<ResultSource, KircApiError> = execute(HttpResponse::toResultSource) {
        client.get("/v2/$repository/manifests/$reference") {
            acceptManifestTypes()
            setAuthSession(currentSession())
        }
    }

    override suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, KircApiError> =
        execute(HttpResponse::body) {
            client.get("/v2/$repository/manifests/$reference") {
                acceptSingleManifestTypes()
                setAuthSession(currentSession())
            }
        }

    override suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: Manifest,
    ): Result<Digest, KircApiError> = execute(HttpResponse::toDigest) {
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
            setAuthSession(currentSession())
        }
    }

    override suspend fun deleteManifest(repository: Repository, reference: Reference): Result<Digest, KircApiError> =
        digest(repository, reference).flatMap { dg ->
            execute({ dg }) {
                client.delete("/v2/$repository/manifests/$dg") {
                    acceptManifestTypes()
                    setAuthSession(currentSession())
                }
            }
        }

    // Blob

    override suspend fun existsBlob(repository: Repository, digest: Digest): Result<*, KircApiError> = execute({}) {
        client.head("/v2/$repository/blobs/$digest") {
            setAuthSession(currentSession())
        }
    }

    override suspend fun blob(repository: Repository, digest: Digest): Result<ByteArray, KircApiError> =
        execute(HttpResponse::body) {
            client.get("/v2/$repository/blobs/$digest") {
                acceptBlobTypes()
                setAuthSession(currentSession())
            }
        }

    override suspend fun <T> blobStream(
        repository: Repository,
        digest: Digest,
        block: suspend (Source) -> T,
    ): Result<T, KircApiError> = try {
        client.prepareGet("/v2/$repository/blobs/$digest") {
            acceptBlobTypes()
            setAuthSession(currentSession())
        }.execute { response ->
            if (response.status.isSuccess()) {
                // The connection stays open for the duration of this lambda.
                // block() must fully consume the Source before returning.
                val source = response.bodyAsChannel().toInputStream().asSource().buffered()
                Result.success(block(source))
            } else {
                Result.failure(response.toErrorResponse())
            }
        }
    } catch (e: Exception) {
        when (e) {
            is KircApiError -> Result.failure(e)
            else -> Result.failure(KircApiError.Unknown(e))
        }
    }

    override suspend fun initiateUpload(repository: Repository): Result<UploadSession, KircApiError> =
        execute(HttpResponse::toUploadSession) {
            client.post("/v2/$repository/blobs/uploads/") {
                setAuthSession(currentSession())
            }
        }

    override suspend fun finishBlobUpload(session: UploadSession, digest: Digest): Result<Digest, KircApiError> =
        execute(HttpResponse::toDigest) {
            client.put(session.location) {
                parameter("digest", digest.toString())
                setAuthSession(currentSession())
            }
        }

    override suspend fun uploadBlobChunked(
        session: UploadSession,
        buffer: Buffer,
        startRange: Long,
        endRange: Long,
    ): Result<UploadSession, KircApiError> = execute(HttpResponse::toUploadSession) {
        client.patch(session.location) {
            header(HttpHeaders.ContentLength, buffer.size)
            header(HttpHeaders.ContentRange, "$startRange-$endRange")
            header(HttpHeaders.ContentType, APPLICATION_OCTET_STREAM)
            setBody(buffer.readByteArray())
            setAuthSession(currentSession())
        }
    }

    override suspend fun uploadBlobStream(
        session: UploadSession,
        path: Path,
        size: Long,
    ): Result<UploadSession, KircApiError> = execute(HttpResponse::toUploadSession) {
        client.patch(session.location) {
            header(HttpHeaders.ContentType, APPLICATION_OCTET_STREAM)
            header(HttpHeaders.ContentLength, size)
            setBody(RepeatableFileContent(path))
            setAuthSession(currentSession())
        }
    }

    override suspend fun uploadStatus(session: UploadSession): Result<Pair<Long, Long>, KircApiError> =
        execute(HttpResponse::toRange) {
            client.get(session.location) {
                setAuthSession(currentSession())
            }
        }

    override suspend fun cancelBlobUpload(session: UploadSession): Result<*, KircApiError> = execute({}) {
        client.delete(session.location) {
            setAuthSession(currentSession())
        }
    }

    // Builder helpers

    private fun HttpRequestBuilder.acceptJson() = header(HttpHeaders.Accept, APPLICATION_JSON)
    private fun HttpRequestBuilder.acceptManifestTypes() = headers {
        appendAll(
            HttpHeaders.Accept,
            listOf(
                APPLICATION_JSON,
                OciManifestV1.MediaType,
                OciManifestListV1.MediaType,
                DockerManifestV2.MediaType,
                DockerManifestListV1.MediaType,
            ),
        )
    }

    private fun HttpRequestBuilder.acceptSingleManifestTypes() = headers {
        appendAll(
            HttpHeaders.Accept,
            listOf(
                APPLICATION_JSON,
                OciManifestV1.MediaType,
                DockerManifestV2.MediaType,
            ),
        )
    }

    private fun HttpRequestBuilder.acceptBlobTypes() = headers {
        appendAll(
            HttpHeaders.Accept,
            listOf(
                APPLICATION_JSON,
                APPLICATION_OCTET_STREAM,
                DockerBlobMediaType,
                OciBlobMediaTypeTar,
                OciBlobMediaTypeGzip,
                OciBlobMediaTypeZstd,
            ),
        )
    }

    private fun HttpRequestBuilder.setAuthSession(authSessionId: UUID) {
        setAttributes {
            put(AuthAttributes.SESSION_ID, authSessionId.toString())
        }
    }
}
