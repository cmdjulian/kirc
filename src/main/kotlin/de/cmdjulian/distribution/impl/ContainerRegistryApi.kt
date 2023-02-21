package de.cmdjulian.distribution.impl

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.result.Result
import de.cmdjulian.distribution.impl.response.Catalog
import de.cmdjulian.distribution.impl.response.TagList
import de.cmdjulian.distribution.model.Digest
import de.cmdjulian.distribution.model.Reference
import de.cmdjulian.distribution.model.Repository
import de.cmdjulian.distribution.spec.manifest.Manifest
import de.cmdjulian.distribution.spec.manifest.ManifestSingle

internal interface ContainerRegistryApi {
    suspend fun ping(): Result<*, FuelError>
    suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, FuelError>
    suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, FuelError>
    suspend fun blob(repository: Repository, digest: Digest): ResponseResultOf<ByteArray>
    suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, FuelError>
    suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, FuelError>
    suspend fun digest(repository: Repository, reference: Reference): Result<Digest, FuelError>
}
