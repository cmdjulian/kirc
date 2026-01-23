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
import io.ktor.util.Attributes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * An AuthProvider that handles Bearer authentication for container registries.
 * This provider extracts the necessary parameters from the WWW-Authenticate header in 401 responses
 * to request new tokens as needed.
 *
 * Tokens are cached for a session based on a unique session ID provided in the request attributes.
 * This allows multiple requests within the same session to reuse the same token until it expires.
 *
 * Request coalescing ensures that simultaneous requests for the same token only trigger a single token retrieval.
 * Subsequent requests wait for the initial request to complete and then use the retrieved token.
 *
 * If a token can't be retrieved, resolved or if the WWW-Authenticate header is missing/invalid,
 * authentication will not be performed.
 *
 * Following attributes can be added to a request which trigger special handling:
 * - [AuthAttributes.SCOPE_REPO] - The repository name for the scope instead of returned scope (e.g. "library/ubuntu")
 * - [AuthAttributes.SCOPE_TYPE] - The scope type for the scope instead of the returned scope (e.g. "pull", "push", "pull,push")
 * - [AuthAttributes.SESSION_ID] - The unique session ID (UUID string) to identify the auth session by unique id
 * - [AuthAttributes.SKIP_REFRESH] - If set to true, the auth refresh will be skipped for this request (e.g. for initial auth requests)
 *
 * [credentials] - The registry credentials to use for token retrieval. If null, no authentication will be performed.
 */
internal sealed class RegistryAuthProvider<T : Any>(private val credentials: RegistryCredentials?) : AuthProvider {

    @Deprecated("Please use sendWithoutRequest function instead")
    override val sendWithoutRequest get() = false

    // Mutex to guard the critical section of checking/creating inflight requests
    protected val requestMutex = Mutex()
    protected val inflightRequests = ConcurrentHashMap<UUID, Deferred<T>>()
    protected val cache: Cache<UUID, T> = Caffeine.newBuilder()
        .expireAfter(
            object : Expiry<UUID, T> {
                override fun expireAfterCreate(key: UUID, value: T, currentTime: Long): Long = expireAfterCreate(value)

                override fun expireAfterUpdate(key: UUID, value: T, currentTime: Long, currentDuration: Long): Long =
                    expireAfterCreate(value)

                override fun expireAfterRead(key: UUID, value: T, currentTime: Long, currentDuration: Long): Long =
                    currentDuration
            },
        ).build()

    abstract val authScheme: String

    abstract fun expireAfterCreate(value: T): Long

    abstract override fun isApplicable(auth: HttpAuthHeader): Boolean

    abstract override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?)

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean {
        // 1. Determine Identity
        val id = request.attributes.getSessionId() ?: return false

        // 2. Fast Path: Check Caffeine cache immediately
        cache.getIfPresent(id)?.let { return true }
        return inflightRequests.contains(id)
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        // 1. Extract the WWW-Authenticate header from the 401 response
        // 2. Parse it into an HttpAuthHeader
        val authHeader = parseAuthHeader(response) ?: return false

        // 3. Ensure it is a bearer / basic challenge
        if (authHeader.authScheme != authScheme) return false

        // 4. Trigger the fetch logic. If resolveToken returns a token, it's now in cache.
        // Check if we should skip auth refresh (e.g. for init auth requests)
        val tokenResolved = resolveToken(response.request.attributes, authHeader) != null
        val skipRefresh = response.request.attributes.getOrNull(AuthAttributes.SKIP_REFRESH) == true
        return tokenResolved && !skipRefresh
    }

    // Helper function to resolve token from attributes and auth header

    protected suspend fun resolveToken(attributes: Attributes, authHeader: HttpAuthHeader?): T? {
        if (credentials == null) return null
        // 1. Determine Identity
        val id = attributes.getSessionId() ?: return null

        // 2. Fast Path: Check Caffeine cache immediately
        cache.getIfPresent(id)?.let { return it }

        // 3. Determine Scope (either from triggered auth challenge to /v2/ or 401 challenge by failed request)
        if (authHeader !is HttpAuthHeader.Parameterized) return null
        val realm = authHeader.parameter("realm") ?: return null
        val scope = buildScope(attributes, authHeader)
        val service = authHeader.parameter("service")

        // 4. Slow Path: Request Coalescing (fetch token)
        // Atomically get existing job or start a new one and
        // wait for the specific key's job to finish
        return coroutineScope {
            // Use Mutex to ensure atomicity when checking/starting jobs
            val job = requestMutex.withLock {
                // Double-check: Did a job start while we were waiting for the lock?
                inflightRequests[id]?.let { return@withLock it }
                // Triple-check: Did a job finish and populate cache while we were waiting?
                cache.getIfPresent(id)?.let { return@withLock CompletableDeferred(it) }

                // Start new job
                val newJob = async {
                    try {
                        val token = requestToken(realm, scope, service, credentials)
                        cache.put(id, token)
                        token
                    } finally {
                        // Re-acquire lock to safely remove from map
                        requestMutex.withLock {
                            inflightRequests.remove(id)
                        }
                    }
                }
                inflightRequests[id] = newJob
                newJob
            }
            job.await()
        }
    }

    protected abstract suspend fun requestToken(
        realm: String,
        scope: String?,
        service: String?,
        credentials: RegistryCredentials,
    ): T

    private fun parseAuthHeader(response: HttpResponse): HttpAuthHeader.Parameterized? {
        val headerValue = response.headers[HttpHeaders.WWWAuthenticate] ?: return null
        return try {
            parseAuthorizationHeader(headerValue) as? HttpAuthHeader.Parameterized
        } catch (_: Exception) {
            null
        }
    }

    private fun buildScope(attributes: Attributes, authHeader: HttpAuthHeader.Parameterized): String? {
        val repo = attributes.getOrNull(AuthAttributes.SCOPE_REPO)
        val type = attributes.getOrNull(AuthAttributes.SCOPE_TYPE)
        return if (repo != null && type != null) {
            "repository:$repo:$type"
        } else {
            authHeader.parameter("scope")
        }
    }

    private fun Attributes.getSessionId(): UUID? = getOrNull(AuthAttributes.SESSION_ID)?.let(UUID::fromString)
}

/**
 * An AuthProvider that handles Bearer authentication for container registries.
 *
 * Implements:
 * - Expiry of token
 * - Adding Bearer auth header to requests
 * - Applicability check for Bearer auth headers
 * - Requesting new Bearer tokens from auth server
 */
internal class RegistryBearerAuthProvider(credentials: RegistryCredentials?) :
    RegistryAuthProvider<BearerToken>(credentials) {

    val authHttpClient by lazy { HttpClient(CIO) }

    override val authScheme: String get() = AuthScheme.Bearer

    override fun expireAfterCreate(value: BearerToken): Long {
        val expiresIn = value.expiresIn?.seconds ?: 5.minutes
        val expiresInWithSafetyMargin = expiresIn - 10.seconds
        return expiresInWithSafetyMargin.inWholeNanoseconds
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        val token = resolveToken(request.attributes, authHeader) ?: return
        request.bearerAuth(token.token)
    }

    override fun isApplicable(auth: HttpAuthHeader): Boolean =
        auth is HttpAuthHeader.Parameterized && auth.authScheme == AuthScheme.Bearer

    override suspend fun requestToken(
        realm: String,
        scope: String?,
        service: String?,
        credentials: RegistryCredentials,
    ): BearerToken = authHttpClient.get(realm) {
        if (scope != null) parameter("scope", scope)
        if (service != null) parameter("service", service)
        basicAuth(credentials.username, credentials.password)
    }.toBearerToken()

    private suspend inline fun HttpResponse.toBearerToken(): BearerToken {
        if (status.value !in 200..299) {
            throw KircApiError.Bearer(
                statusCode = status.value,
                url = request.url,
                method = request.method,
                message = "Could not retrieve bearer token from auth server (status=${bodyAsText()})",
            )
        }
        return try {
            // using bodyAsText because client for bearer retrieval isn't configured with json feature
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

/**
 * An AuthProvider that handles Basic authentication for container registries.
 *
 * This provider uses the provided registry credentials to add Basic Auth headers to requests.
 *
 * Implements:
 * - Expiry of token
 * - Adding Basic auth header to requests
 * - Applicability check for Basic auth headers
 * - Returning credentials as token
 */
internal class RegistryBasicAuthProvider(credentials: RegistryCredentials?) :
    RegistryAuthProvider<RegistryCredentials>(credentials) {

    override val authScheme: String get() = AuthScheme.Basic

    override fun expireAfterCreate(value: RegistryCredentials): Long = 5.minutes.inWholeNanoseconds

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        val token = resolveToken(request.attributes, authHeader) ?: return
        request.basicAuth(token.username, token.password)
    }

    override fun isApplicable(auth: HttpAuthHeader): Boolean = auth.authScheme == AuthScheme.Basic

    override suspend fun requestToken(
        realm: String,
        scope: String?,
        service: String?,
        credentials: RegistryCredentials,
    ): RegistryCredentials = credentials
}
