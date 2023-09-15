package de.cmdjulian.kirc.impl

import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.Encoding
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.core.awaitResponseResult
import com.github.kittinunf.fuel.core.extensions.AuthenticatedRequest
import com.github.kittinunf.result.getOrNull
import com.github.kittinunf.result.map
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.utils.CaseInsensitiveMap
import im.toss.http.parser.HttpAuthCredentials

internal class ResponseRetryWithAuthentication(
    private val credentials: RegistryCredentials?,
    private val fuelManager: FuelManager,
) {
    suspend fun <T : Any> retryOnUnauthorized(
        responseResult: ResponseResultOf<T>,
        deserializer: Deserializable<T>,
    ): ResponseResultOf<T> {
        val (request, response, _) = responseResult
        val headers = CaseInsensitiveMap(response.headers)

        if (response.statusCode == 401 && "www-authenticate" in headers) {
            val retryableRequest = retryRequest(headers["www-authenticate"]?.first(), request)
            retryableRequest?.let { return it.awaitResponseResult(deserializer) }
        }

        return responseResult
    }

    private suspend fun retryRequest(header: String?, request: Request): Request? {
        val wwwAuth = header?.runCatching { HttpAuthCredentials.parse(this) }?.getOrNull()

        return when (wwwAuth?.scheme) {
            "Basic" -> resolveBasicAuth(request)
            "Bearer" -> resolveTokenAuth(wwwAuth, request)
            else -> null
        }
    }

    private fun resolveBasicAuth(request: Request): Request? {
        if (credentials == null) return null

        return AuthenticatedRequest(request.clone(fuelManager)).basic(credentials.username, credentials.password)
    }

    private suspend fun resolveTokenAuth(wwwAuth: HttpAuthCredentials, request: Request): Request? {
        val realm = wwwAuth.singleValueParams["realm"]!!.replace("\"", "")
        val scope = wwwAuth.singleValueParams["scope"]?.replace("\"", "")
        val service = wwwAuth.singleValueParams["service"]?.replace("\"", "")

        class TokenResponse(val token: String)

        val parameters = buildList {
            if (scope != null) add("scope" to scope)
            if (service != null) add("service" to service)
        }
        val token = fuelManager.get(realm, parameters)
            .let { credentials?.run { AuthenticatedRequest(it).basic(username, password) } ?: it }
            .awaitResponseResult(jacksonDeserializer<TokenResponse>())
            .third
            .map(TokenResponse::token)
            .getOrNull()

        return token?.let { AuthenticatedRequest(request.clone(FuelManager.instance)).bearer(token) }
    }
}

private fun Request.clone(fuelManager: FuelManager): Request {
    val encoding = Encoding(httpMethod = method, urlString = url.toString())
    return fuelManager.request(encoding)
        .header(Headers.from(request.headers))
        .requestProgress(request.executionOptions.requestProgress)
        .responseProgress(request.executionOptions.responseProgress)
        .let { if (!body.isEmpty() && !body.isConsumed()) it.body(request.body) else it }
}
