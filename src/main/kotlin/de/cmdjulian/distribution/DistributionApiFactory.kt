package de.cmdjulian.distribution

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.haroldadmin.cnradapter.NetworkResponseAdapterFactory
import de.cmdjulian.distribution.impl.DistributionApi
import de.cmdjulian.distribution.impl.DistributionClientImpl
import de.cmdjulian.distribution.impl.DockerImageClientImpl
import de.cmdjulian.distribution.model.config.ProxyConfig
import de.cmdjulian.distribution.model.config.RegistryCredentials
import de.cmdjulian.distribution.model.oci.DockerImageSlug
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
import java.net.URL

private val client = OkHttpClient.Builder()
    .apply {
        interceptors().add { chain ->
            chain.request().newBuilder().header("Accept", "application/json").build().let(chain::proceed)
        }
    }
    .build()

internal val mapper = jsonMapper {
    addModules(kotlinModule())
    addModules(JavaTimeModule())
    addModules(Jdk8Module())
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenResponse(val token: String)

object DistributionApiFactory {

    private val DOCKER_HUB_URL = URL("https://registry-1.docker.io")

    /**
     * Create a DistributionClient for a registry. If no args are supplied the client is constructed for Docker Hub with
     * no auth.
     */
    fun create(url: URL = DOCKER_HUB_URL, credentials: RegistryCredentials? = null, config: ProxyConfig? = null):
        DistributionClient {
        val httpClient = OkHttpClient.Builder()
            .proxy(config?.proxy)
            .apply { config?.authenticator?.run { proxyAuthenticator(this) } }
            .apply { interceptors().add(interceptor(credentials)) }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(url.let { if (it.toString() == "https://docker.io") DOCKER_HUB_URL else it })
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .addCallAdapterFactory(NetworkResponseAdapterFactory())
            .client(httpClient)
            .build()
        val api = retrofit.create(DistributionApi::class.java)

        return DistributionClientImpl(api)
    }

    fun create(
        image: DockerImageSlug,
        credentials: RegistryCredentials? = null,
        config: ProxyConfig? = null,
        insecure: Boolean = false
    ): DockerImageClient = DockerImageClientImpl(
        create(
            URL((if (insecure) "http://" else "https://") + image.registry.toString()),
            credentials,
            config
        ) as DistributionClientImpl,
        image
    )

    private fun interceptor(credentials: RegistryCredentials?) = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401) {
            val wwwAuthHeader = response.anyHeader("Www-Authenticate", "www-authenticate", "WWW-Authenticate")

            if (wwwAuthHeader != null) {
                val wwwAuth = HttpAuthCredentials.parse(wwwAuthHeader)

                return@Interceptor when {
                    wwwAuth.scheme == "Basic" && credentials != null -> {
                        response.close()
                        chain.retryWithBasicAuth(credentials)
                    }

                    wwwAuth.scheme == "Bearer" -> {
                        response.close()
                        chain.retryWithTokenAuth(credentials, wwwAuth)
                    }

                    else -> response
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

    private fun Chain.retryWithTokenAuth(
        credentials: RegistryCredentials?,
        wwwAuth: HttpAuthCredentials
    ): Response {
        val tokenAuthRequest = tokenAuthRequest(credentials, wwwAuth)
        val response = client.newCall(tokenAuthRequest).execute()

        if (response.isSuccessful) {
            val tokenResponse: TokenResponse = mapper.readValue(response.body!!.string())
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

        fun Request.Builder.basicAuth(credentials: RegistryCredentials?) = if (credentials != null) {
            addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
        } else {
            this
        }

        return Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .basicAuth(credentials)
            .build()
    }
}

@Suppress("SameParameterValue")
private fun Response.anyHeader(vararg headers: String): String? {
    for (header in headers) {
        val candidate = header(header)
        if (candidate != null) return candidate
    }

    return null
}
