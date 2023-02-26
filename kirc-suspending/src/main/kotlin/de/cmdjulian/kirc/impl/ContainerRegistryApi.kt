package de.cmdjulian.kirc.impl

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle

internal interface ContainerRegistryApi {
    suspend fun ping(): Result<*, FuelError>
    suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, FuelError>
    suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, FuelError>
    suspend fun blob(repository: Repository, digest: Digest): Result<ByteArray, FuelError>
    suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, FuelError>
    suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, FuelError>
    suspend fun digest(repository: Repository, reference: Reference): Result<Digest, FuelError>
}
