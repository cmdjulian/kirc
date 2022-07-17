package de.cmdjulian.distribution

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.haroldadmin.cnradapter.NetworkResponseAdapterFactory
import de.cmdjulian.distribution.impl.DOCKER_HUB_URL
import de.cmdjulian.distribution.impl.DistributionApi
import de.cmdjulian.distribution.impl.DistributionClientImpl
import de.cmdjulian.distribution.impl.DockerImageClientImpl
import de.cmdjulian.distribution.impl.httpClient
import de.cmdjulian.distribution.impl.jsonMapper
import de.cmdjulian.distribution.model.config.ProxyConfig
import de.cmdjulian.distribution.model.config.RegistryCredentials
import de.cmdjulian.distribution.model.oci.DockerImageSlug
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
import java.net.URL

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenResponse(val token: String)

@Suppress("unused", "MemberVisibilityCanBePrivate", "HttpUrlsUsage")
object DistributionApiFactory {

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
            .baseUrl(url.run { if ("$this" == "https://docker.io") DOCKER_HUB_URL else this })
            .addConverterFactory(JacksonConverterFactory.create(jsonMapper))
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
        var response = chain.proceed(request)

        if (response.code == 401) {
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
        val response = httpClient.newCall(tokenAuthRequest).execute()

        if (response.isSuccessful) {
            val tokenResponse: TokenResponse = jsonMapper.readValue(response.body!!.string())
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
