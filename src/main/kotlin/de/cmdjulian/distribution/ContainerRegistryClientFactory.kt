package de.cmdjulian.distribution

import com.github.kittinunf.fuel.core.FoldableResponseInterceptor
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.RequestFactory
import com.github.kittinunf.fuel.core.ResponseTransformer
import com.github.kittinunf.fuel.core.response
import com.github.kittinunf.result.getOrNull
import com.github.kittinunf.result.map
import de.cmdjulian.distribution.config.ProxyConfig
import de.cmdjulian.distribution.config.RegistryCredentials
import de.cmdjulian.distribution.impl.ContainerRegistryApi
import de.cmdjulian.distribution.impl.ContainerRegistryApiImpl
import de.cmdjulian.distribution.impl.CoroutineContainerRegistryClientImpl
import de.cmdjulian.distribution.impl.CoroutineImageClientImpl
import de.cmdjulian.distribution.impl.jacksonDeserializer
import de.cmdjulian.distribution.model.DockerImageSlug
import de.cmdjulian.distribution.utils.CaseInsensitiveMap
import im.toss.http.parser.HttpAuthCredentials
import java.net.URL
import java.util.Base64

const val DOCKER_HUB_URL = "https://registry-1.docker.io"

@Suppress("unused", "MemberVisibilityCanBePrivate", "HttpUrlsUsage")
object ContainerRegistryClientFactory {

    data class ImageClientConfig(
        val image: DockerImageSlug,
        val credentials: RegistryCredentials? = null,
        val config: ProxyConfig? = null,
        val insecure: Boolean = false
    )

    /**
     * Create a DistributionClient for a registry. If no args are supplied the client is constructed for Docker Hub with
     * no auth.
     */
    fun create(
        url: URL = URL(DOCKER_HUB_URL),
        credentials: RegistryCredentials? = null,
        config: ProxyConfig? = null
    ): CoroutineContainerRegistryClient {
        val fuel = FuelManager().apply {
            basePath = if (this.toString() == "https://docker.io") DOCKER_HUB_URL else url.toString()
            proxy = config?.proxy
            addResponseInterceptor(AuthenticationInterceptor(credentials, this))
        }
        val api: ContainerRegistryApi = ContainerRegistryApiImpl(fuel)

        return CoroutineContainerRegistryClientImpl(api)
    }

    fun create(image: DockerImageSlug): CoroutineImageClient = create(ImageClientConfig(image))

    fun create(config: ImageClientConfig): CoroutineImageClient {
        val client = create(
            URL((if (config.insecure) "http://" else "https://") + config.image.registry.toString()),
            config.credentials,
            config.config
        )

        return CoroutineImageClientImpl(client as CoroutineContainerRegistryClientImpl, config.image)
    }
}

private class AuthenticationInterceptor(
    private val credentials: RegistryCredentials?,
    private val manager: FuelManager
) : FoldableResponseInterceptor {
    override fun invoke(next: ResponseTransformer): ResponseTransformer {
        return inner@{ req, res ->
            val headers = CaseInsensitiveMap(res.headers)
            if (res.statusCode == 401 && "www-authenticate" in headers) {
                resolveToken(headers["www-authenticate"]?.first())?.let { token ->
                    val convertible = object : RequestFactory.RequestConvertible {
                        override val request get() = req
                    }
                    val retriedRequest = manager.request(convertible).appendHeader(Headers.AUTHORIZATION, token)

                    return@inner next(req, retriedRequest.response().second)
                }
            }

            next(req, res)
        }
    }

    private fun resolveToken(header: String?): String? {
        if (header == null) return null

        val wwwAuth = HttpAuthCredentials.parse(header)
        val basicAuthHeader = credentials?.let {
            "Basic ${Base64.getEncoder().encodeToString("${it.username}:${it.password}".toByteArray())}"
        }

        return when {
            wwwAuth.scheme == "Basic" && basicAuthHeader != null -> basicAuthHeader
            wwwAuth.scheme == "Bearer" -> resolveTokenAuth(basicAuthHeader, wwwAuth)
            else -> null
        }
    }

    private fun resolveTokenAuth(authHeader: String?, wwwAuth: HttpAuthCredentials): String? {
        val realm = wwwAuth.singleValueParams["realm"]!!.replace("\"", "")
        val service = wwwAuth.singleValueParams["service"]!!.replace("\"", "")
        val scope = wwwAuth.singleValueParams["scope"]!!.replace("\"", "")

        class TokenResponse(val token: String)
        return FuelManager.instance.get(realm, listOf("service" to service, "scope" to scope))
            .apply { if (authHeader != null) appendHeader(Headers.AUTHORIZATION, authHeader) }
            .response(jacksonDeserializer<TokenResponse>())
            .third
            .map(TokenResponse::token)
            .getOrNull()
    }
}
