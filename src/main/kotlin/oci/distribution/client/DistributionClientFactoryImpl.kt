package oci.distribution.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import im.toss.http.parser.HttpAuthCredentials
import oci.distribution.client.model.domain.Proxy
import oci.distribution.client.model.domain.RegistryCredentials
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.net.URL

private val client = OkHttpClient()
val mapper = jsonMapper {
    addModules(kotlinModule())
    addModules(JavaTimeModule())
    addModules(Jdk8Module())
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenResponse(val token: String)

object DistributionClientFactory {

    // TODO https://stackoverflow.com/a/35567936/9153701
    fun create(url: URL, credentials: RegistryCredentials? = null, proxy: Proxy? = null): DistributionClient {
        val okHttpClient = OkHttpClient.Builder().apply { interceptors().add(interceptor(credentials)) }
        val retrofit = Retrofit.Builder()
            .baseUrl("$url")
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .client(okHttpClient.build())
            .build()
        val api = retrofit.create(DistributionApi::class.java)

        return DistributionClientImpl(api)
    }

    private fun interceptor(credentials: RegistryCredentials?) = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code() == 401) {
            val wwwAuthHeader = response.header("Www-Authenticate", response.header("www-authenticate"))

            if (wwwAuthHeader != null) {
                val wwwAuth = HttpAuthCredentials.parse(wwwAuthHeader)

                response.close()
                return@Interceptor when {
                    wwwAuth.scheme == "Basic" && credentials != null -> chain.retryWithBasicAuth(credentials)
                    wwwAuth.scheme == "Bearer" -> chain.retryWithTokenAuth(credentials, wwwAuth)
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

    private fun Chain.retryWithTokenAuth(credentials: RegistryCredentials?, wwwAuth: HttpAuthCredentials): Response {
        val tokenAuthRequest = tokenAuthRequest(credentials, wwwAuth)
        val response = client.newCall(tokenAuthRequest).execute()

        if (response.isSuccessful) {
            val token = mapper.readValue(response.body()!!.string(), TokenResponse::class.java).token
            val request = request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()

            return proceed(request)
        }

        return response
    }

    private fun tokenAuthRequest(credentials: RegistryCredentials?, wwwAuth: HttpAuthCredentials): Request {
        val realm = wwwAuth.singleValueParams["realm"]!!.replace("\"", "")
        val service = wwwAuth.singleValueParams["service"]!!.replace("\"", "")
        val scope = wwwAuth.singleValueParams["scope"]!!.replace("\"", "")

        val url = HttpUrl.parse(realm)!!.newBuilder()
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
