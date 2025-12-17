package de.cmdjulian.kirc.utils

import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.impl.KircApiError
import de.cmdjulian.kirc.impl.response.TokenResponse
import im.toss.http.parser.HttpAuthCredentials
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal fun CIOEngineConfig.configureHttps(skipTlsVerify: Boolean, keystore: KeyStore?) {
    when {
        skipTlsVerify -> https {
            trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}

                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        }

        keystore != null -> https {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManager = runCatching {
                tmf.init(keystore)
                tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
            }.getOrElse { keyStoreException ->
                throw KircApiError.Unknown(keyStoreException)
            }
        }
    }
}

internal fun Auth.configureAuth(credentials: RegistryCredentials?) {
    basic {
        credentials {
            if (credentials != null) {
                BasicAuthCredentials(username = credentials.username, password = credentials.password)
            } else {
                null
            }
        }
    }
    bearer {
        val semaphore = Semaphore(1)
        val bearerTokenStorage = mutableListOf<BearerTokens>()
        loadTokens {
            semaphore.withPermit {
                bearerTokenStorage.firstOrNull()
            }
        }
        refreshTokens {
            if (credentials != null) {
                semaphore.withPermit {
                    bearerAuth(credentials).also(bearerTokenStorage::add)
                }
            } else {
                null
            }
        }
    }
}

private suspend fun RefreshTokensParams.bearerAuth(credentials: RegistryCredentials): BearerTokens {
    val authHeader = if (response.headers.caseInsensitiveName) {
        response.headers[HttpHeaders.WWWAuthenticate.lowercase()]
    } else {
        response.headers[HttpHeaders.WWWAuthenticate]
    }

    when {
        response.status.value == 401 && authHeader == null -> throw KircApiError.Header(
            statusCode = response.status.value,
            url = response.request.url,
            method = response.request.method,
            message = "Received 401 Unauthorized but no WWW-Authenticate header present",
        )

        response.status.value != 401 ->
            error("Unexpected status code ${response.status.value} when requesting bearer token")
    }

    val (realm, scope, service) = parseWWWAuthHeader(authHeader)

    // request token via credentials if available
    val result = getBearerToken(realm, scope, service, credentials)

    return BearerTokens(result.token, result.token)
}

// HELPER

private data class RegistryAuthCredentials(val realm: String, val scope: String?, val service: String?)

private fun RefreshTokensParams.parseWWWAuthHeader(header: String?): RegistryAuthCredentials {
    val wwwAuth = header?.runCatching(HttpAuthCredentials::parse)?.getOrElse {
        throw KircApiError.Header(
            statusCode = response.status.value,
            url = response.request.url,
            method = response.request.method,
            message = "Could not parse WWW-Authenticate header: $header",
        )
    }

    val realm = wwwAuth?.singleValueParams["realm"]?.trim('"') ?: throw KircApiError.Header(
        statusCode = response.status.value,
        url = response.request.url,
        method = response.request.method,
        message = "WWW-Authenticate header does not contain realm: $header",
    )
    val scope = wwwAuth.singleValueParams["scope"]?.trim('"')
    val service = wwwAuth.singleValueParams["service"]?.trim('"')

    return RegistryAuthCredentials(realm = realm, scope = scope, service = service)
}

private suspend fun RefreshTokensParams.getBearerToken(
    realm: String,
    scope: String?,
    service: String?,
    credentials: RegistryCredentials,
): TokenResponse = runCatching {
    client.get(realm) {
        if (scope != null) parameter("scope", scope)
        if (service != null) parameter("service", service)
        header(HttpHeaders.Authorization, basicAuth(credentials.username, credentials.password))
        markAsRefreshTokenRequest()
    }
}.map { response ->
    if (response.status.isError()) {
        throw KircApiError.Bearer(
            statusCode = response.status.value,
            url = response.request.url,
            method = response.request.method,
            message = "Could not retrieve bearer token (status=${response.bodyAsText()})",
        )
    }
    runCatching { response.body<TokenResponse>() }.getOrElse {
        throw KircApiError.Json(
            statusCode = response.status.value,
            url = response.request.url,
            method = response.request.method,
            message = "Could not deserialize bearer token response",
            cause = it,
        )
    }
}.getOrThrow()

private fun basicAuth(user: String, pass: String): String =
    "Basic " + java.util.Base64.getEncoder().encodeToString("$user:$pass".toByteArray())

private fun HttpStatusCode.isError(): Boolean = value !in 200..299
