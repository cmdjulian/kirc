package de.cmdjulian.kirc.impl

import com.github.kittinunf.result.Result
import de.cmdjulian.kirc.client.RegistryCredentials
import im.toss.http.parser.HttpAuthCredentials
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import java.net.URI

internal class ResponseRetryWithAuthentication(
    private val credentials: RegistryCredentials?,
    private val client: HttpClient,
    private val baseUrl: URI,
) {
    suspend inline fun execute(crossinline block: suspend () -> HttpResponse): Result<HttpResponse, KtorHttpError> =
        runCatching { performWithAuthRetry(block) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { throwable ->
                if (throwable is KtorHttpError) Result.failure(throwable) else Result.failure(
                    KtorHttpError(-1, Url(baseUrl), HttpMethod("?"), ByteArray(0), throwable),
                )
            },
        )

    private suspend inline fun performWithAuthRetry(crossinline block: suspend () -> HttpResponse): HttpResponse {
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
        val retry = buildAuthRetryRequest(authHeader, first.request) ?: throw first.toError()
        return retry().let { second ->
            if (second.status.isError()) throw second.toError()
            second
        }
    }

    private suspend fun buildAuthRetryRequest(header: String?, original: HttpRequest): (suspend () -> HttpResponse)? {
        val wwwAuth = header?.runCatching { HttpAuthCredentials.parse(this) }?.getOrNull()
        return when (wwwAuth?.scheme) {
            "Basic" -> basicRetry(original)
            "Bearer" -> bearerRetry(wwwAuth, original)
            else -> null
        }
    }

    // BASIC AUTH

    private fun basicRetry(original: HttpRequest): (suspend () -> HttpResponse)? {
        // if no credentials are available, we cannot retry (because first attempt ran into auth error)
        if (credentials == null) return null

        return suspend {
            client.request {
                clone(original)
                header(HttpHeaders.Authorization, basicAuth(credentials.username, credentials.password))
            }
        }
    }

    private fun basicAuth(user: String, pass: String): String =
        "Basic " + java.util.Base64.getEncoder().encodeToString("$user:$pass".toByteArray())

    // BEARER AUTH

    private suspend fun bearerRetry(
        wwwAuth: HttpAuthCredentials,
        original: HttpRequest,
    ): (suspend () -> HttpResponse)? {
        val token = bearer(wwwAuth) ?: return null

        return suspend {
            client.request {
                clone(original)
                header(HttpHeaders.Authorization, "Bearer $token")
            }
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

    private fun HttpRequestBuilder.clone(original: HttpRequest) {
        method = original.method
        url(original.url)
        headers.appendAll(original.headers)
        setBody(original.content)
    }

    private suspend fun HttpResponse.toError(): KtorHttpError = KtorHttpError(
        status.value,
        request.url,
        request.method,
        runCatching { bodyAsText().toByteArray() }.getOrElse { ByteArray(0) },
        null,
    )
}
