package de.cmdjulian.distribution

import com.fasterxml.jackson.module.kotlin.readValue
import com.haroldadmin.cnradapter.NetworkResponseAdapterFactory
import de.cmdjulian.distribution.config.ProxyConfig
import de.cmdjulian.distribution.config.RegistryCredentials
import de.cmdjulian.distribution.impl.ContainerRegistryApi
import de.cmdjulian.distribution.impl.CoroutineContainerRegistryClientImpl
import de.cmdjulian.distribution.impl.CoroutineImageClientImpl
import de.cmdjulian.distribution.impl.JsonMapper
import de.cmdjulian.distribution.model.DockerImageSlug
import de.cmdjulian.distribution.utils.getIgnoreCase
import im.toss.http.parser.HttpAuthCredentials
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.URL

const val DOCKER_HUB_URL = "https://registry-1.docker.io"

@Suppress("unused", "MemberVisibilityCanBePrivate", "HttpUrlsUsage")
object ContainerRegistryClientFactory {

    data class ImageClientConfig(
        val image: DockerImageSlug,
        val credentials: RegistryCredentials? = null,
        val config: ProxyConfig? = null,
        val insecure: Boolean = false
    )

    private object AcceptApplicationJsonInterceptor : Interceptor {
        override fun intercept(chain: Chain): Response {
            return chain.request()
                .newBuilder()
                .header("Accept", "application/json")
                .build()
                .let(chain::proceed)
        }
    }

    internal val HttpClient = OkHttpClient.Builder()
        .addInterceptor(AcceptApplicationJsonInterceptor)
        .build()

    /**
     * Create a DistributionClient for a registry. If no args are supplied the client is constructed for Docker Hub with
     * no auth.
     */
    fun create(
        url: URL = URL(DOCKER_HUB_URL),
        credentials: RegistryCredentials? = null,
        config: ProxyConfig? = null
    ): CoroutineContainerRegistryClient {
        val baseUrl = when (this.toString()) {
            "https://docker.io" -> URL(DOCKER_HUB_URL)
            else -> url
        }
        val httpClient = OkHttpClient.Builder()
            .proxy(config?.proxy)
            .apply { config?.authenticator?.run { proxyAuthenticator(this) } }
            .apply { interceptors().add(interceptor(credentials)) }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(JacksonConverterFactory.create(JsonMapper))
            .addCallAdapterFactory(NetworkResponseAdapterFactory())
            .client(httpClient)
            .build()
        val api = retrofit.create(ContainerRegistryApi::class.java)

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

    private fun interceptor(credentials: RegistryCredentials?) = Interceptor { chain ->
        val request = chain.request()
        var response = chain.proceed(request)

        if (response.code == HTTP_UNAUTHORIZED) {
            response.headers.getIgnoreCase("www-authenticate")?.let { header ->
                val wwwAuth = HttpAuthCredentials.parse(header)

                if (wwwAuth.scheme in arrayOf("Basic", "Bearer")) response.close()
                when {
                    wwwAuth.scheme == "Basic" && credentials != null -> response = chain.retryWithBasicAuth(credentials)
                    wwwAuth.scheme == "Bearer" -> response = chain.retryWithTokenAuth(credentials, wwwAuth)
                }
            }
        }

        return@Interceptor response
    }

    private fun Chain.retryWithBasicAuth(credentials: RegistryCredentials): Response {
        return request().newBuilder()
            .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
            .build()
            .let(this::proceed)
    }

    private fun Chain.retryWithTokenAuth(credentials: RegistryCredentials?, wwwAuth: HttpAuthCredentials): Response {
        val tokenAuthRequest = tokenAuthRequest(credentials, wwwAuth)
        val response = HttpClient.newCall(tokenAuthRequest).execute()

        if (response.isSuccessful) {
            class TokenResponse(val token: String)

            val tokenResponse: TokenResponse = JsonMapper.readValue(response.body!!.string())
            val request = request().newBuilder()
                .addHeader("Authorization", "Bearer ${tokenResponse.token}")
                .build()

            return proceed(request)
        }

        return response
    }

    private fun tokenAuthRequest(credentials: RegistryCredentials?, wwwAuth: HttpAuthCredentials): Request {
        val realm = wwwAuth.singleValueParams["realm"]!!.replace("\"", "")
        val service = wwwAuth.singleValueParams["service"]!!.replace("\"", "")
        val scope = wwwAuth.singleValueParams["scope"]!!.replace("\"", "")

        val url = realm.toHttpUrl()
            .newBuilder()
            .addQueryParameter("service", service)
            .addQueryParameter("scope", scope)
            .build()

        fun Request.Builder.basicAuth(credentials: RegistryCredentials?): Request.Builder =
            if (credentials == null) this
            else addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))

        return Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .basicAuth(credentials)
            .build()
    }
}
