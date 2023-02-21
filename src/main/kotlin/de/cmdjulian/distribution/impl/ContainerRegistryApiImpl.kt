package de.cmdjulian.distribution.impl

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Parameters
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.core.awaitResponseResult
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.core.deserializers.ByteArrayDeserializer
import com.github.kittinunf.fuel.core.deserializers.EmptyDeserializer
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
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

private const val ACCEPT_HEADER = "Accept"
private const val APPLICATION_JSON = "application/json"
private const val APPLICATION_OCTET_STREAM = "application/octet-stream"

internal class ContainerRegistryApiImpl(private val fuelManager: FuelManager) : ContainerRegistryApi {
    override suspend fun ping(): Result<*, FuelError> = fuelManager.get("/").awaitResult(EmptyDeserializer)

    override suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, FuelError> {
        val parameter: Parameters = buildList {
            if (limit != null) add("n" to limit)
            if (last != null) add("last" to last)
        }

        return fuelManager.get("/_catalog", parameter)
            .appendHeader(ACCEPT_HEADER, APPLICATION_JSON)
            .awaitResult(jacksonDeserializer())
    }

    override suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, FuelError> {
        val parameter: Parameters = buildList {
            if (limit != null) add("n" to limit)
            if (last != null) add("last" to last)
        }

        return fuelManager.get("/$repository/tags/list", parameter)
            .appendHeader(ACCEPT_HEADER, APPLICATION_JSON)
            .awaitResult(jacksonDeserializer())
    }

    override suspend fun blob(repository: Repository, digest: Digest): ResponseResultOf<ByteArray> {
        return fuelManager.get("/$repository/blobs/$digest")
            .appendHeader(
                ACCEPT_HEADER,
                APPLICATION_JSON,
                APPLICATION_OCTET_STREAM,
                DockerBlobMediaType,
                OciBlobMediaTypeTar,
                OciBlobMediaTypeGzip,
                OciBlobMediaTypeZstd
            )
            .awaitResponseResult(ByteArrayDeserializer())
    }

    override suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, FuelError> {
        return fuelManager.get("/$repository/manifests/$reference")
            .appendHeader(
                ACCEPT_HEADER,
                APPLICATION_JSON,
                DockerManifestV2.MediaType,
                DockerManifestListV1.MediaType,
                OciManifestV1.MediaType,
                OciManifestListV1.MediaType
            )
            .awaitResult(jacksonDeserializer())
    }

    override suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, FuelError> {
        return fuelManager.get("/$repository/manifests/$reference")
            .appendHeader(ACCEPT_HEADER, APPLICATION_JSON, DockerManifestV2.MediaType, OciManifestV1.MediaType)
            .awaitResult(jacksonDeserializer())
    }

    override suspend fun digest(repository: Repository, reference: Reference): Result<Digest, FuelError> {
        val response = fuelManager.head("/$repository/manifests/$reference")
            .appendHeader(
                ACCEPT_HEADER,
                APPLICATION_JSON,
                DockerManifestV2.MediaType,
                DockerManifestListV1.MediaType,
                OciManifestV1.MediaType,
                OciManifestListV1.MediaType
            )
            .awaitResponseResult(EmptyDeserializer)

        return response.third.map { response.second["Docker-content-digest"].single().let(::Digest) }
    }
}

internal inline fun <reified T : Any> jacksonDeserializer() = object : ResponseDeserializable<T> {
    override fun deserialize(content: String): T = JsonMapper.readValue(content)
}
