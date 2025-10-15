package de.cmdjulian.kirc.impl

import com.github.kittinunf.result.Result
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.impl.serialization.jacksonDeserializer
import de.cmdjulian.kirc.spec.RegistryErrorResponse
import im.toss.http.parser.HttpAuthCredentials
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal class ResponseRetryWithAuthentication(
    private val credentials: RegistryCredentials?,
    private val client: HttpClient,
    private val baseUrl: URI,
) {
    suspend inline fun execute(
        body: RequestBodyType = RequestBodyType.Binary,
        crossinline block: suspend () -> HttpResponse,
    ): Result<HttpResponse, KircApiError> =
        runCatching { performWithAuthRetry(body, block) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { throwable ->
                if (throwable is KircApiError) {
                    Result.failure(throwable)
                } else {
                    Result.failure(KircApiError.Network(Url(baseUrl), HttpMethod("?"), throwable))
                }
            },
        )

    private suspend inline fun performWithAuthRetry(
        body: RequestBodyType,
        crossinline block: suspend () -> HttpResponse,
    ): HttpResponse {
        val first = block()
        val wwwAuth = HttpHeaders.WWWAuthenticate
        val authHeader =
            if (first.headers.caseInsensitiveName) first.headers[wwwAuth.lowercase()] else first.headers[wwwAuth]

        // Returns result if there's no error and is authenticated
        if (first.status.value != 401 || authHeader == null) {
            if (first.status.isError()) throw first.toError()
            return first
        }

        // Retry with authentication
        val retry = buildAuthRetryRequest(body, authHeader, first.request) ?: throw first.toError()
        return retry.execute().let { second ->
            if (second.status.isError()) throw second.toError()
            second
        }
    }

    private suspend fun buildAuthRetryRequest(
        body: RequestBodyType,
        header: String?,
        original: HttpRequest,
    ): HttpStatement? {
        val wwwAuth = header?.runCatching { HttpAuthCredentials.parse(this) }?.getOrNull()
        return when (wwwAuth?.scheme) {
            "Basic" -> basicRetry(body, original)
            "Bearer" -> bearerRetry(body, wwwAuth, original)
            else -> null
        }
    }

    // BASIC AUTH

    private suspend fun basicRetry(body: RequestBodyType, original: HttpRequest): HttpStatement? {
        // if no credentials are available, we cannot retry (because first attempt ran into auth error)
        if (credentials == null) return null

        return client.prepareRequest {
            clone(body, original)
            header(HttpHeaders.Authorization, basicAuth(credentials.username, credentials.password))
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun basicAuth(user: String, pass: String): String =
        "Basic " + Base64.encode("$user:$pass".toByteArray())

    // BEARER AUTH

    private suspend fun bearerRetry(
        body: RequestBodyType,
        wwwAuth: HttpAuthCredentials,
        original: HttpRequest,
    ): HttpStatement? {
        val token = bearer(wwwAuth) ?: return null

        return client.prepareRequest {
            clone(body, original)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    @ReflectionHint
    private class TokenResponse(val token: String)

    private suspend fun bearer(wwwAuth: HttpAuthCredentials): String? {
        val realm = wwwAuth.singleValueParams["realm"]?.trim('"') ?: return null
        val scope = wwwAuth.singleValueParams["scope"]?.trim('"')
        val service = wwwAuth.singleValueParams["service"]?.trim('"')

        // request token via credentials if available
        return runCatching {
            val response = client.get(realm) {
                if (scope != null) parameter("scope", scope)
                if (service != null) parameter("service", service)
                if (credentials != null) {
                    header(HttpHeaders.Authorization, basicAuth(credentials.username, credentials.password))
                }
            }
            if (response.status.isError()) throw response.toError()
            jacksonDeserializer<TokenResponse>().deserialize(response.bodyAsText()).token
        }.getOrNull()
    }

    // HELPER

    private fun HttpStatusCode.isError(): Boolean = value !in 200..299

    private suspend fun HttpRequestBuilder.clone(body: RequestBodyType, original: HttpRequest) {
        method = original.method
        url(original.url)
        headers.appendAll(original.headers)
        when (body) {
            // original binary data still present, so we can reuse it
            is RequestBodyType.Binary -> setBody(original.content)
            // original body is already consumed, so we need to use the channel provider
            is RequestBodyType.Stream -> setBody(body.channel())
        }
    }

    private suspend fun HttpResponse.toError(): KircApiError = KircApiError.Registry(
        statusCode = status.value,
        url = request.url,
        method = request.method,
        body = runCatching { jacksonDeserializer<RegistryErrorResponse>().deserialize(bodyAsText()) }.getOrElse {
            RegistryErrorResponse(emptyList())
        },
    )
}
