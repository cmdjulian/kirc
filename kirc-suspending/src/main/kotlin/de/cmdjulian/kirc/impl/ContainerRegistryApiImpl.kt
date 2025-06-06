package de.cmdjulian.kirc.impl

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Parameters
import com.github.kittinunf.fuel.core.awaitResponseResult
import com.github.kittinunf.fuel.core.deserializers.ByteArrayDeserializer
import com.github.kittinunf.fuel.core.deserializers.EmptyDeserializer
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.spec.blob.DockerBlobMediaType
import de.cmdjulian.kirc.spec.blob.OciBlobMediaTypeGzip
import de.cmdjulian.kirc.spec.blob.OciBlobMediaTypeTar
import de.cmdjulian.kirc.spec.blob.OciBlobMediaTypeZstd
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestV2
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import de.cmdjulian.kirc.spec.manifest.OciManifestV1
import java.util.*

private const val APPLICATION_JSON = "application/json"
private const val APPLICATION_OCTET_STREAM = "application/octet-stream"

internal class ContainerRegistryApiImpl(private val fuelManager: FuelManager, credentials: RegistryCredentials?) :
    ContainerRegistryApi {

    private val handler = ResponseRetryWithAuthentication(credentials, fuelManager)

    override suspend fun ping(): Result<*, FuelError> = fuelManager.get("/v2/")
        .awaitResponseResult(EmptyDeserializer)
        .let { responseResult -> handler.retryOnUnauthorized(responseResult, EmptyDeserializer) }
        .third

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

    override suspend fun existsBlob(repository: Repository, digest: Digest): Result<*, FuelError> =
        fuelManager.head("/v2/$repository/blobs/$digest")
            .awaitResponseResult(EmptyDeserializer)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, EmptyDeserializer) }
            .third

    // Pull

    override suspend fun blob(repository: Repository, digest: Digest): Result<ByteArray, FuelError> {
        val deserializable = ByteArrayDeserializer()
        return fuelManager.get("/v2/$repository/blobs/$digest")
            .appendHeader(
                Headers.ACCEPT,
                APPLICATION_JSON,
                APPLICATION_OCTET_STREAM,
                DockerBlobMediaType,
                OciBlobMediaTypeTar,
                OciBlobMediaTypeGzip,
                OciBlobMediaTypeZstd,
            )
            .awaitResponseResult(deserializable)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, deserializable) }
            .third
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
                OciManifestListV1.MediaType,
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

    override suspend fun deleteManifest(repository: Repository, reference: Reference): Result<Digest, FuelError> {
        return digest(repository, reference).flatMap { digest ->
            fuelManager.delete("/v2/$repository/manifests/$digest")
                .appendHeader(
                    Headers.ACCEPT,
                    APPLICATION_JSON,
                    DockerManifestV2.MediaType,
                    DockerManifestListV1.MediaType,
                    OciManifestV1.MediaType,
                    OciManifestListV1.MediaType,
                )
                .awaitResponseResult(EmptyDeserializer)
                .let { responseResult -> handler.retryOnUnauthorized(responseResult, EmptyDeserializer) }
                .third
                .map { digest }
        }
    }

    override suspend fun digest(repository: Repository, reference: Reference) = when (reference) {
        is Digest -> Result.success(reference)
        is Tag -> {
            val response = fuelManager.head("/v2/$repository/manifests/$reference")
                .appendHeader(
                    Headers.ACCEPT,
                    APPLICATION_JSON,
                    DockerManifestV2.MediaType,
                    DockerManifestListV1.MediaType,
                    OciManifestV1.MediaType,
                    OciManifestListV1.MediaType,
                )
                .awaitResponseResult(EmptyDeserializer)
                .let { responseResult -> handler.retryOnUnauthorized(responseResult, EmptyDeserializer) }

            response.third.map { response.second["Docker-content-digest"].single().let(::Digest) }
        }
    }

    // Upload

    override suspend fun initiateUpload(
        repository: Repository,
        from: Repository?,
        mount: Digest?,
    ): Result<UUID?, FuelError> {
        require((from == null && mount == null) || (from != null && mount != null)) {
            "Please provide both 'from' and 'mount' parameters to initiate upload or neither of them"
        }
        val parameters = buildList {
            if (from != null) {
                add("from" to from)
                add("mount" to mount)
            }
        }
        val response = fuelManager.post("/v2/$repository/blobs/uploads", parameters)
            .awaitResponseResult(EmptyDeserializer)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, EmptyDeserializer) }

        return response.third.map {
            when (val statusCode = response.second.statusCode) {
                // Blob successfully mounted from another repository
                201 -> null
                // Upload initiated successfully (or fallback if mount fails)
                202 -> response.second["Docker-Upload-UUID"].single().let(UUID::fromString)

                else -> error("Unknown status code ($statusCode) encountered during initiate upload")
            }
        }
    }

    override suspend fun uploadBlob(
        repository: Repository,
        sessionUUID: UUID,
        digest: Digest,
        blob: ByteArray?,
    ): Result<Digest, FuelError> {
        val parameters = listOf("digest" to digest)
        val response = fuelManager.put("/v2/$repository/blobs/uploads/$sessionUUID", parameters)
            .appendHeader(Headers.ACCEPT, APPLICATION_OCTET_STREAM)
            .apply { if (blob != null) body(blob) }
            .awaitResponseResult(EmptyDeserializer)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, EmptyDeserializer) }

        return response.third.map { response.second["Docker-Content-Digest"].single().let(::Digest) }
    }

    override suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle,
    ): Result<Digest, FuelError> {
        val contentType = when (manifest) {
            is DockerManifestV2 -> DockerManifestV2.MediaType
            is OciManifestV1 -> OciManifestV1.MediaType
        }
        val response = fuelManager.put("/v2/$repository/manifests/$reference")
            .appendHeader(Headers.CONTENT_TYPE, contentType)
            .body(JsonMapper.writeValueAsString(manifest))
            .awaitResponseResult(EmptyDeserializer)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, EmptyDeserializer) }

        return response.third.map { response.second["Docker-Content-Digest"].single().let(::Digest) }
    }

    override suspend fun cancelBlobUpload(repository: Repository, sessionUUID: UUID): Result<*, FuelError> =
        fuelManager.delete("/v2/$repository/blobs/uploads/$sessionUUID")
            .awaitResponseResult(EmptyDeserializer)
            .let { responseResult -> handler.retryOnUnauthorized(responseResult, EmptyDeserializer) }
            .third
}
