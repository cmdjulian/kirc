package de.cmdjulian.kirc.impl

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.ResultSource
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.impl.response.UploadSession
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
import im.toss.http.parser.HttpAuthCredentials
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.asInputStream
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

    private suspend inline fun <reified T> execute(
        crossinline block: suspend () -> HttpResponse,
        noinline transform: suspend (HttpResponse) -> T,
    ): Result<T, KtorHttpError> {
        return runCatching { performWithAuthRetry(block, transform) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { throwable ->
                if (throwable is KtorHttpError) Result.failure(throwable) else Result.failure(
                    KtorHttpError(-1, "${baseUrl}", "?", ByteArray(0), throwable),
                )
            },
        )
    }

    private suspend inline fun <reified T> performWithAuthRetry(
        crossinline block: suspend () -> HttpResponse,
        noinline transform: suspend (HttpResponse) -> T,
    ): T {
        val first = block()
        if (first.status.value != 401 || first.headers["WWW-Authenticate"].isNullOrBlank()) {
            if (!first.status.isSuccess()) throw first.toError()
            return transform(first)
        }

        val header = first.headers["WWW-Authenticate"]
        val retry = buildAuthRetryRequest(header, first.request)
        if (retry == null) {
            throw first.toError()
        }
        val second = retry()
        if (!second.status.isSuccess()) throw second.toError()
        return transform(second)
    }

    private suspend fun buildAuthRetryRequest(
        header: String?,
        original: HttpRequest,
    ): (suspend () -> HttpResponse)? {
        val wwwAuth = header?.runCatching { HttpAuthCredentials.parse(this) }?.getOrNull()
        return when (wwwAuth?.scheme) {
            "Basic" -> {
                if (credentials == null) null else {
                    suspend {
                        client.request {
                            method = original.method
                            url(original.url)
                            headers.appendAll(original.headers)
                            setBodyIfPresent(original)
                            header(HttpHeaders.Authorization, basic(credentials.username, credentials.password))
                        }
                    }
                }
            }

            "Bearer" -> bearerRetry(wwwAuth, original)
            else -> null
        }
    }

    private fun basic(user: String, pass: String): String =
        "Basic " + java.util.Base64.getEncoder().encodeToString("$user:$pass".toByteArray())

    private suspend fun bearerRetry(
        wwwAuth: HttpAuthCredentials,
        original: HttpRequest,
    ): (suspend () -> HttpResponse)? {
        val realm = wwwAuth.singleValueParams["realm"]?.trim('"') ?: return null
        val scope = wwwAuth.singleValueParams["scope"]?.trim('"')
        val service = wwwAuth.singleValueParams["service"]?.trim('"')
        val token = runCatching {
            client.get(realm) {
                if (scope != null) parameter("scope", scope)
                if (service != null) parameter("service", service)
                if (credentials != null) {
                    header(HttpHeaders.Authorization, basic(credentials.username, credentials.password))
                }
            }.let { resp ->
                if (!resp.status.isSuccess()) throw resp.toError()
                JsonMapper.readTree(resp.bodyAsText())["token"].asText()
            }
        }.getOrNull() ?: return null

        return suspend {
            client.request {
                method = original.method
                url(original.url)
                headers.appendAll(original.headers)
                setBodyIfPresent(original)
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    private suspend fun HttpResponse.toError(): KtorHttpError = KtorHttpError(
        status.value,
        request.url.toString(),
        request.method.value,
        runCatching { bodyAsText().toByteArray() }.getOrElse { ByteArray(0) },
        null,
    )

    private suspend fun setBodyIfPresent(original: HttpRequest) {
        // Body re-use not supported via public API; for current usage (no retry with body except PUT/POST of small JSON) we ignore.
    }

    // Status

    override suspend fun ping(): Result<*, KtorHttpError> = execute(
        block = { client.get("/v2/") },
        transform = { Unit },
    )

    override suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, KtorHttpError> = execute(
        block = {
            client.get("/v2/_catalog") {
                if (limit != null) parameter("n", limit)
                if (last != null) parameter("last", last)
                acceptJson()
            }
        },
        transform = { it.body() },
    )

    override suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, KtorHttpError> =
        execute(
            block = {
                client.get("/v2/$repository/tags/list") {
                    if (limit != null) parameter("n", limit)
                    if (last != null) parameter("last", last)
                    acceptJson()
                }
            },
            transform = { it.body() },
        )

    override suspend fun digest(repository: Repository, reference: Reference): Result<Digest, KtorHttpError> =
        when (reference) {
            is Digest -> Result.success(reference)
            is Tag -> execute(
                block = {
                    client.head("/v2/$repository/manifests/$reference") { acceptManifestTypes() }
                },
                transform = { resp ->
                    resp.headers["Docker-Content-Digest"]?.let(::Digest)
                        ?: throw KtorHttpError(
                            resp.status.value,
                            resp.request.url.toString(),
                            resp.request.method.value,
                            ByteArray(0),
                            IllegalStateException("Missing Docker-Content-Digest header"),
                        )
                },
            )
        }

    // Manifest

    override suspend fun existsManifest(
        repository: Repository,
        reference: Reference,
        accept: String,
    ): Result<*, KtorHttpError> = execute(
        block = { client.head("/v2/$repository/manifests/$reference") { header(HttpHeaders.Accept, accept) } },
        transform = { },
    )

    override suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, KtorHttpError> =
        execute(
            block = {
                client.get("/v2/$repository/manifests/$reference") { acceptManifestTypes() }
            },
            transform = { it.body() },
        )

    override suspend fun manifestStream(
        repository: Repository,
        reference: Reference,
    ): Result<ResultSource, KtorHttpError> = execute(
        block = { client.get("/v2/$repository/manifests/$reference") { acceptManifestTypes() } },
        transform = { resp ->
            val size = resp.headers[HttpHeaders.ContentLength]?.toLong()
                ?: throw KtorHttpError(
                    resp.status.value,
                    resp.request.url.toString(),
                    resp.request.method.value,
                    ByteArray(0),
                    IllegalStateException("Missing Content-Length"),
                )
            ResultSource(resp.bodyAsChannel().toInputStream().asSource().buffered(), size)
        },
    )

    override suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, KtorHttpError> =
        execute(
            block = { client.get("/v2/$repository/manifests/$reference") { acceptSingleManifestTypes() } },
            transform = { it.body() },
        )

    override suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: Manifest,
    ): Result<Digest, KtorHttpError> = execute(
        block = {
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
        },
        transform = { resp ->
            resp.headers["Docker-Content-Digest"]?.let(::Digest)
                ?: throw KtorHttpError(
                    resp.status.value,
                    resp.request.url.toString(),
                    resp.request.method.value,
                    ByteArray(0),
                    IllegalStateException("Missing Docker-Content-Digest header"),
                )
        },
    )

    override suspend fun deleteManifest(repository: Repository, reference: Reference): Result<Digest, KtorHttpError> =
        digest(repository, reference).flatMap { dg ->
            execute(
                block = { client.delete("/v2/$repository/manifests/$dg") { acceptManifestTypes() } },
                transform = { dg },
            )
        }

    // Blob

    override suspend fun existsBlob(repository: Repository, digest: Digest): Result<*, KtorHttpError> = execute(
        block = { client.head("/v2/$repository/blobs/$digest") },
        transform = { Unit },
    )

    override suspend fun blob(repository: Repository, digest: Digest): Result<ByteArray, KtorHttpError> = execute(
        block = {
            client.get("/v2/$repository/blobs/$digest") { acceptBlobTypes() }
        },
        transform = { it.body() },
    )

    override suspend fun blobStream(repository: Repository, digest: Digest): Result<Source, KtorHttpError> = execute(
        block = { client.get("/v2/$repository/blobs/$digest") { acceptBlobTypes() } },
        transform = { resp -> resp.bodyAsChannel().toInputStream().asSource().buffered() },
    )

    override suspend fun initiateUpload(repository: Repository): Result<UploadSession, KtorHttpError> = execute(
        block = { client.post("/v2/$repository/blobs/uploads/") },
        transform = { resp ->
            UploadSession(
                sessionId = resp.headers["Docker-Upload-UUID"] ?: throw KtorHttpError(
                    resp.status.value,
                    resp.request.url.toString(),
                    resp.request.method.value,
                    ByteArray(0),
                    IllegalStateException("Missing Docker-Upload-UUID"),
                ),
                location = resp.headers[HttpHeaders.Location] ?: throw KtorHttpError(
                    resp.status.value,
                    resp.request.url.toString(),
                    resp.request.method.value,
                    ByteArray(0),
                    IllegalStateException("Missing Location header"),
                ),
            )
        },
    )

    override suspend fun finishBlobUpload(session: UploadSession, digest: Digest): Result<Digest, KtorHttpError> =
        execute(
            block = { client.put(session.location) { parameter("digest", digest.toString()) } },
            transform = { resp ->
                resp.headers["Docker-Content-Digest"]?.let(::Digest)
                    ?: throw KtorHttpError(
                        resp.status.value,
                        resp.request.url.toString(),
                        resp.request.method.value,
                        ByteArray(0),
                        IllegalStateException("Missing Docker-Content-Digest header"),
                    )
            },
        )

    override suspend fun uploadBlobChunked(
        session: UploadSession,
        buffer: Buffer,
        startRange: Long,
        endRange: Long,
    ): Result<UploadSession, KtorHttpError> = execute(
        block = {
            client.patch(session.location) {
                header(HttpHeaders.ContentLength, buffer.size)
                header("Content-Range", "$startRange-$endRange")
                header(HttpHeaders.ContentType, APPLICATION_OCTET_STREAM)
                setBody(buffer.readByteArray())
            }
        },
        transform = { resp ->
            UploadSession(
                sessionId = resp.headers["Docker-Upload-UUID"] ?: session.sessionId,
                location = resp.headers[HttpHeaders.Location] ?: session.location,
            )
        },
    )

    override suspend fun uploadBlobStream(
        session: UploadSession,
        source: Source,
    ): Result<UploadSession, KtorHttpError> = execute(
        block = {
            client.patch(session.location) {
                header(HttpHeaders.ContentType, APPLICATION_OCTET_STREAM)
                setBody(source.asInputStream().readBytes()) // fallback to buffering entire stream
            }
        },
        transform = { resp ->
            UploadSession(
                sessionId = resp.headers["Docker-Upload-UUID"] ?: session.sessionId,
                location = resp.headers[HttpHeaders.Location] ?: session.location,
            )
        },
    )

    override suspend fun uploadStatus(session: UploadSession): Result<Pair<Long, Long>, KtorHttpError> = execute(
        block = { client.get(session.location) },
        transform = { resp ->
            val rangeHeader = resp.headers["Range"] ?: throw KtorHttpError(
                resp.status.value,
                resp.request.url.toString(),
                resp.request.method.value,
                ByteArray(0),
                IllegalStateException("Missing Range header"),
            )
            val rangeValue = if (rangeHeader.startsWith("bytes=")) rangeHeader.removePrefix("bytes=") else rangeHeader
            val parts = rangeValue.split('-')
            val from = parts.getOrNull(0)?.toLongOrNull() ?: throw IllegalStateException("Range[0]")
            val to = parts.getOrNull(1)?.toLongOrNull() ?: throw IllegalStateException("Range[1]")
            from to to
        },
    )

    override suspend fun cancelBlobUpload(session: UploadSession): Result<*, KtorHttpError> = execute(
        block = { client.delete(session.location) },
        transform = { Unit },
    )

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
