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
import java.util.*

/**
 * Defines the calls to the container registry API
 *
 * Wraps calls in a [Result]
 *
 * see https://docs.docker.com/reference/api/registry/latest
 */
internal interface ContainerRegistryApi {

    // Status

    suspend fun ping(): Result<*, FuelError>
    suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, FuelError>
    suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, FuelError>
    suspend fun digest(repository: Repository, reference: Reference): Result<Digest, FuelError>

    // Manifest

    suspend fun existsManifest(repository: Repository, reference: Reference, accept: String): Result<*, FuelError>
    suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, FuelError>
    suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, FuelError>
    suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle,
    ): Result<Digest, FuelError>

    suspend fun deleteManifest(repository: Repository, reference: Reference): Result<Digest, FuelError>

    // Blob

    suspend fun existsBlob(repository: Repository, digest: Digest): Result<*, FuelError>
    suspend fun blob(repository: Repository, digest: Digest): Result<ByteArray, FuelError>
    suspend fun initiateUpload(
        repository: Repository,
        from: Repository?,
        mount: Digest?,
    ): Result<UUID?, FuelError>

    suspend fun uploadBlob(
        repository: Repository,
        sessionUUID: UUID,
        digest: Digest,
        blob: ByteArray?,
    ): Result<Digest, FuelError>

    suspend fun cancelBlobUpload(repository: Repository, sessionUUID: UUID): Result<*, FuelError>
}
