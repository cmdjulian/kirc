package de.cmdjulian.kirc.impl.auth

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.impl.KircApiError
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// todo custom auth provider for basic auth to make use of session ID caching too?
/**
 * An AuthProvider that handles Bearer authentication for container registries.
 * This provider extracts the necessary parameters from the WWW-Authenticate header in 401 responses
 * to request new tokens as needed.
 *
 * Bearer tokens are cached based on a unique session ID provided in the request attributes.
 * This allows multiple requests within the same session to reuse the same token until it expires.
 *
 * Request coalescing ensures that simultaneous requests for the same token only trigger a single token retrieval.
 * Subsequent requests wait for the initial request to complete and then use the retrieved token.
 *
 * If a token can't be retrieved, resolved or if the WWW-Authenticate header is missing/invalid,
 * authentication will not be performed.
 *
 * [credentials] - The registry credentials to use for token retrieval. If null, no authentication will be performed.
 */
internal class RegistryBearerAuthProvider(private val credentials: RegistryCredentials?) : AuthProvider {

    @Deprecated("Please use sendWithoutRequest function instead")
    override val sendWithoutRequest get() = false

    private val inflightRequests = ConcurrentHashMap<UUID, Deferred<BearerToken>>()
    private val cache: Cache<UUID, BearerToken> = Caffeine.newBuilder()
        .expireAfter(
            object : Expiry<UUID, BearerToken> {
                override fun expireAfterCreate(key: UUID, value: BearerToken, currentTime: Long): Long {
                    val expiresAt =
                        value.expiresAt()?.toInstant()?.toEpochMilli() ?: return 5.minutes.inWholeMilliseconds
                    val now = Clock.systemDefaultZone().instant().toEpochMilli()
                    val safetyMargin = 10.seconds.inWholeMilliseconds
                    return expiresAt - now - safetyMargin
                }

                override fun expireAfterUpdate(
                    key: UUID,
                    value: BearerToken,
                    currentTime: Long,
                    currentDuration: Long,
                ): Long = expireAfterCreate(key, value, currentDuration)

                override fun expireAfterRead(
                    key: UUID,
                    value: BearerToken,
                    currentTime: Long,
                    currentDuration: Long,
                ): Long = currentDuration
            },
        )
        .build()

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean {
        // 1. Determine Identity
        val id = request.attributes.getOrNull<String>(AttributeKey("AuthSessionId"))
            ?.let(UUID::fromString)
            ?: return false

        // 2. Fast Path: Check Caffeine cache immediately
        cache.getIfPresent(id)?.let { return true }
        return inflightRequests.contains(id)
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        // 1. Extract the WWW-Authenticate header from the 401 response
        val headerValue = response.headers[HttpHeaders.WWWAuthenticate] ?: return false

        // 2. Parse it into an HttpAuthHeader
        val authHeader = try {
            parseAuthorizationHeader(headerValue)
        } catch (_: Exception) {
            return false
        }

        // 3. Ensure it is a Bearer challenge
        if (authHeader !is HttpAuthHeader.Parameterized || authHeader.authScheme != AuthScheme.Bearer) {
            return false
        }

        // 4. Trigger the fetch logic. If resolveToken returns a token, it's now in cache.
        // We use the attributes from the ORIGINAL request (response.request) to maintain session ID.
        return resolveToken(response.request.attributes, authHeader) != null
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        if (authHeader !is HttpAuthHeader.Parameterized) return
        val token = resolveToken(request.attributes, authHeader) ?: return
        request.bearerAuth(token.token)
    }

    override fun isApplicable(auth: HttpAuthHeader): Boolean =
        auth is HttpAuthHeader.Parameterized && auth.authScheme == AuthScheme.Bearer

    private suspend fun resolveToken(attributes: Attributes, authHeader: HttpAuthHeader.Parameterized?): BearerToken? {
        if (credentials == null) return null
        // 1. Determine Identity
        val id = attributes.getOrNull<String>(AttributeKey("AuthSessionId"))
            ?.let(UUID::fromString)
            ?: return null

        // 2. Fast Path: Check Caffeine cache immediately
        cache.getIfPresent(id)?.let { return it }

        // 3. Determine Scope (from 401 challenge)
        val realm = authHeader?.parameter("realm") ?: return null
        val scope = authHeader.parameter("scope")
        val service = authHeader.parameter("service")

        // 4. Slow Path: Request Coalescing (fetch token)
        // Atomically get existing job or start a new one and
        // wait for the specific key's job to finish
        return coroutineScope {
            inflightRequests.computeIfAbsent(id) {
                async {
                    try {
                        // Fetch the token
                        val token = requestBearerToken(realm, scope, service, credentials)
                        // Update Cache
                        cache.put(id, token)
                        token
                    } finally {
                        // Always remove from keys-in-flight map when done
                        inflightRequests.remove(id)
                    }
                }
            }.await()
        }
    }

    private suspend fun requestBearerToken(
        realm: String,
        scope: String?,
        service: String?,
        credentials: RegistryCredentials,
    ): BearerToken = HttpClient(CIO).get(realm) {
        if (scope != null) parameter("scope", scope)
        if (service != null) parameter("service", service)
        basicAuth(credentials.username, credentials.password)
    }.toBearerToken()

    private suspend fun HttpResponse.toBearerToken() = if (status.value !in 200..299) {
        throw KircApiError.Bearer(
            statusCode = status.value,
            url = request.url,
            method = request.method,
            message = "Could not retrieve bearer token from auth server (status=${bodyAsText()})",
        )
    } else {
        try {
            JsonMapper.readValue<BearerToken>(bodyAsText())
        } catch (e: Exception) {
            throw KircApiError.Json(
                statusCode = status.value,
                url = request.url,
                method = request.method,
                message = "Could not deserialize bearer token response",
                cause = e,
            )
        }
    }
}
