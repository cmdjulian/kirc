package de.cmdjulian.distribution.impl

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.Encoding
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Parameters
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.core.awaitResponseResult
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.core.deserializers.ByteArrayDeserializer
import com.github.kittinunf.fuel.core.deserializers.EmptyDeserializer
import com.github.kittinunf.fuel.core.extensions.AuthenticatedRequest
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.getOrNull
import com.github.kittinunf.result.map
import de.cmdjulian.distribution.config.RegistryCredentials
import de.cmdjulian.distribution.impl.response.Catalog
import de.cmdjulian.distribution.impl.response.TagList
import de.cmdjulian.distribution.model.Digest
import de.cmdjulian.distribution.model.Reference
import de.cmdjulian.distribution.model.Repository
import de.cmdjulian.distribution.spec.blob.DockerBlobMediaType
import de.cmdjulian.distribution.spec.blob.OciBlobMediaTypeGzip
import de.cmdjulian.distribution.spec.blob.OciBlobMediaTypeTar
import de.cmdjulian.distribution.spec.blob.OciBlobMediaTypeZstd
import de.cmdjulian.distribution.spec.manifest.DockerManifestListV1
import de.cmdjulian.distribution.spec.manifest.DockerManifestV2
import de.cmdjulian.distribution.spec.manifest.Manifest
import de.cmdjulian.distribution.spec.manifest.ManifestSingle
import de.cmdjulian.distribution.spec.manifest.OciManifestListV1
import de.cmdjulian.distribution.spec.manifest.OciManifestV1
import de.cmdjulian.distribution.utils.CaseInsensitiveMap
import im.toss.http.parser.HttpAuthCredentials

private const val APPLICATION_JSON = "application/json"
private const val APPLICATION_OCTET_STREAM = "application/octet-stream"

internal class ContainerRegistryApiImpl(private val fuelManager: FuelManager, credentials: RegistryCredentials?) :
    ContainerRegistryApi {

    private val handler = ResponseRetryWithAuthentication(credentials, fuelManager)

    override suspend fun ping(): Result<*, FuelError> = fuelManager.get("/").awaitResult(EmptyDeserializer)

    override suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, FuelError> {
        val parameter: Parameters = buildList {
            if (limit != null) add("n" to limit)
            if (last != null) add("last" to last)
        }

        val deserializable = jacksonDeserializer<Catalog>()
        return fuelManager.get("/v2/_catalog", parameter)
            .appendHeader(Headers.ACCEPT, APPLICATION_JSON)
            .awaitResponseResult(deserializable)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, deserializable) }
            .third
    }

    override suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, FuelError> {
        val parameter: Parameters = buildList {
            if (limit != null) add("n" to limit)
            if (last != null) add("last" to last)
        }

        val deserializable = jacksonDeserializer<TagList>()
        return fuelManager.get("/v2/$repository/tags/list", parameter)
            .appendHeader(Headers.ACCEPT, APPLICATION_JSON)
            .awaitResponseResult(deserializable)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, deserializable) }
            .third
    }

    override suspend fun blob(repository: Repository, digest: Digest): ResponseResultOf<ByteArray> {
        val deserializable = ByteArrayDeserializer()
        return fuelManager.get("/v2/$repository/blobs/$digest")
            .appendHeader(
                Headers.ACCEPT,
                APPLICATION_JSON,
                APPLICATION_OCTET_STREAM,
                DockerBlobMediaType,
                OciBlobMediaTypeTar,
                OciBlobMediaTypeGzip,
                OciBlobMediaTypeZstd
            )
            .awaitResponseResult(deserializable)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, deserializable) }
    }

    override suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, FuelError> {
        val deserializable = jacksonDeserializer<Manifest>()
        return fuelManager.get("/v2/$repository/manifests/$reference")
            .appendHeader(
                Headers.ACCEPT,
                APPLICATION_JSON,
                DockerManifestV2.MediaType,
                DockerManifestListV1.MediaType,
                OciManifestV1.MediaType,
                OciManifestListV1.MediaType
            )
            .awaitResponseResult(deserializable)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, deserializable) }
            .third
    }

    override suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, FuelError> {
        val deserializable = jacksonDeserializer<ManifestSingle>()
        return fuelManager.get("/v2/$repository/manifests/$reference")
            .appendHeader(Headers.ACCEPT, APPLICATION_JSON, DockerManifestV2.MediaType, OciManifestV1.MediaType)
            .awaitResponseResult(deserializable)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, deserializable) }
            .third
    }

    override suspend fun digest(repository: Repository, reference: Reference): Result<Digest, FuelError> {
        val response = fuelManager.head("/v2/$repository/manifests/$reference")
            .appendHeader(
                Headers.ACCEPT,
                APPLICATION_JSON,
                DockerManifestV2.MediaType,
                DockerManifestListV1.MediaType,
                OciManifestV1.MediaType,
                OciManifestListV1.MediaType
            )
            .awaitResponseResult(EmptyDeserializer)
            .let { responseResult ->
                val resultTriple = handler.retryOnUnauthorized(responseResult, EmptyDeserializer)
                resultTriple
            }

        return response.third.map { response.second["Docker-content-digest"].single().let(::Digest) }
    }
}

internal inline fun <reified T : Any> jacksonDeserializer() = object : ResponseDeserializable<T> {
    override fun deserialize(content: String): T = JsonMapper.readValue(content)
}

private class ResponseRetryWithAuthentication(
    private val credentials: RegistryCredentials?,
    private val fuelManager: FuelManager
) {

    suspend fun <T : Any> retryOnUnauthorized(
        responseResult: ResponseResultOf<T>,
        deserializer: Deserializable<T>
    ): ResponseResultOf<T> {
        val (request, response, _) = responseResult
        val headers = CaseInsensitiveMap(response.headers)

        if (response.statusCode == 401 && "www-authenticate" in headers) {
            val retryableRequest = retryRequest(headers["www-authenticate"]?.first(), request)
            retryableRequest?.let { return it.awaitResponseResult(deserializer) }
        }

        return responseResult
    }

    suspend fun retryRequest(header: String?, request: Request): Request? {
        if (header == null) return null

        val wwwAuth = HttpAuthCredentials.parse(header)
        return when (wwwAuth.scheme) {
            "Basic" -> resolveBasicAuth(request)
            "Bearer" -> resolveTokenAuth(wwwAuth, request)
            else -> null
        }
    }

    private fun resolveBasicAuth(request: Request): Request? {
        if (credentials == null) return null

        return AuthenticatedRequest(request.clone(fuelManager)).basic(credentials.username, credentials.password)
    }

    suspend fun resolveTokenAuth(wwwAuth: HttpAuthCredentials, request: Request): Request? {
        val realm = wwwAuth.singleValueParams["realm"]!!.replace("\"", "")
        val service = wwwAuth.singleValueParams["service"]!!.replace("\"", "")
        val scope = wwwAuth.singleValueParams["scope"]!!.replace("\"", "")

        class TokenResponse(val token: String)

        val token = FuelManager.instance.get(realm, listOf("service" to service, "scope" to scope))
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
