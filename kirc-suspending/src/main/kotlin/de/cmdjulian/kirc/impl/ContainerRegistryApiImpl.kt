package de.cmdjulian.kirc.impl

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Parameters
import com.github.kittinunf.fuel.core.awaitResponseResult
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.core.deserializers.ByteArrayDeserializer
import com.github.kittinunf.fuel.core.deserializers.EmptyDeserializer
import com.github.kittinunf.result.Result
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
        val digest = digest(repository, reference)

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

        return digest
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
                .let { responseResult ->
                    val resultTriple = handler.retryOnUnauthorized(responseResult, EmptyDeserializer)
                    resultTriple
                }

            response.third.map { response.second["Docker-content-digest"].single().let(::Digest) }
        }
    }
}
