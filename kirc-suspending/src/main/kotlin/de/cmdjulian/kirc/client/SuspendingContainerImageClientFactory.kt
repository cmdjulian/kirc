package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.impl.ContainerRegistryApiImpl
import de.cmdjulian.kirc.impl.KircApiError
import de.cmdjulian.kirc.impl.SuspendingContainerImageClientImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageRegistryClientImpl
import de.cmdjulian.kirc.impl.response.TokenResponse
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.impl.serialization.jacksonDeserializer
import im.toss.http.parser.HttpAuthCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.engine.http
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.Path

object SuspendingContainerImageClientFactory {

    const val DOCKER_HUB_REGISTRY_URL = "https://registry.hub.docker.com/"

    /**
     * Create a ContainerRegistryClient for a registry. If no args are supplied the client is constructed for Docker
     * Hub with no authentication.
     */
    @JvmStatic
    fun create(
        url: URI = URI(DOCKER_HUB_REGISTRY_URL),
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
        timeout: Duration = Duration.ofSeconds(5),
        tmpPath: Path = Path(System.getProperty("java.io.tmpdir")),
    ): SuspendingContainerImageRegistryClient {
        require(keystore == null || !skipTlsVerify) { "can not skip tls verify if a keystore is set" }
        require(timeout.toMillis() in Int.MIN_VALUE..Int.MAX_VALUE) { "timeout in ms has to be a valid int" }

        val client = HttpClient(CIO) {
            engine {
                requestTimeout = timeout.toMillis()
                if (proxy != null) {
                    val address = proxy.address()
                    if (address is java.net.InetSocketAddress) {
                        this.proxy = ProxyBuilder.http("http://${address.hostString}:${address.port}")
                    }
                }
                configureHttps(skipTlsVerify, keystore)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = timeout.toMillis()
                connectTimeoutMillis = timeout.toMillis()
                socketTimeoutMillis = timeout.toMillis()
            }
            install(ContentNegotiation) {
                // re-use existing ObjectMapper instance
                register(ContentType.Application.Json, JacksonConverter(JsonMapper))
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            defaultRequest {
                url(url.toString())
            }
            install(Auth) {
                configureAuth(credentials)
            }
        }

        val api = ContainerRegistryApiImpl(client)
        return SuspendingContainerImageRegistryClientImpl(api, tmpPath)
    }

    private fun CIOEngineConfig.configureHttps(skipTlsVerify: Boolean, keystore: KeyStore?) {
        if (skipTlsVerify) {
            https {
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}

                    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
            }
        } else if (keystore != null) {
            https {
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(keystore)
                trustManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
            }
        }
    }

    private fun Auth.configureAuth(credentials: RegistryCredentials?) {
        basic {
            credentials {
                if (credentials != null) {
                    BasicAuthCredentials(username = credentials.username, password = credentials.password)
                } else {
                    null
                }
            }
            sendWithoutRequest { request ->
                "v2" in request.url.pathSegments
            }
        }
        bearer {
            val bearerTokenStorage = mutableListOf<BearerTokens>()
            loadTokens(bearerTokenStorage::lastOrNull)
            refreshTokens {
                if (credentials != null) {
                    bearerAuth(credentials)?.also(bearerTokenStorage::add)
                } else {
                    null
                }
            }
            sendWithoutRequest { request ->
                "v2" in request.url.pathSegments
            }
        }
    }

    private suspend fun RefreshTokensParams.bearerAuth(credentials: RegistryCredentials): BearerTokens? {
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

    private data class RegistryAuthCredentials(
        val realm: String,
        val scope: String?,
        val service: String?,
    )

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
        response.bodyAsText().runCatching(jacksonDeserializer<TokenResponse>()::deserialize).getOrElse {
            throw KircApiError.Json(
                statusCode = response.status.value,
                url = response.request.url,
                method = response.request.method,
                message = "Could not deserialize bearer token response",
                cause = it,
            )
        }
    }.getOrThrow()

    @OptIn(ExperimentalEncodingApi::class)
    private fun basicAuth(user: String, pass: String): String = "Basic " + Base64.encode("$user:$pass".toByteArray())

    private fun HttpStatusCode.isError(): Boolean = value !in 200..299

    @JvmStatic
    suspend fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
        timeout: Duration = Duration.ofSeconds(5),
        tmpPath: Path = Path(System.getProperty("java.io.tmpdir")),
    ): SuspendingContainerImageClient {
        val url = "${if (insecure) "http://" else "https://"}${image.registry}"
        val client = create(URI(url), credentials, proxy, skipTlsVerify, keystore, timeout, tmpPath)

        return SuspendingContainerImageClientImpl(client, image)
    }
}
