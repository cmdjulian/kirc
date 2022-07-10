package oci.distribution.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import im.toss.http.parser.HttpAuthCredentials
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
private val mapper = ObjectMapper()

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenResponse(val token: String)

object DistributionClientFactory {

    fun create(url: URL, credentials: RegistryCredentials? = null): DistributionClient {
        val okHttpClient = OkHttpClient.Builder().apply { interceptors().add(interceptor(credentials)) }
        val retrofit = Retrofit.Builder()
            .baseUrl(url.toString())
            .addConverterFactory(JacksonConverterFactory.create())
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
        val realm = wwwAuth.singleValueParams["realm"]!!
        val service = wwwAuth.singleValueParams["service"]
        val scope = wwwAuth.singleValueParams["scope"]

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
