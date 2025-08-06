package de.cmdjulian.kirc.impl

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.TagList
import de.cmdjulian.kirc.impl.response.UploadSession
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.io.Buffer
import kotlinx.io.Source

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

    /** Retrieve certain (single) image manfiest */
    suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, FuelError>

    /** Retrieve any image manifest matching reference */
    suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, FuelError>
    suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: Manifest,
    ): Result<Digest, FuelError>

    suspend fun deleteManifest(repository: Repository, reference: Reference): Result<Digest, FuelError>

    // Blob

    suspend fun existsBlob(repository: Repository, digest: Digest): Result<*, FuelError>

    /** Retrieve blob data as [ByteArray] (loaded into memory) */
    suspend fun blob(repository: Repository, digest: Digest): Result<ByteArray, FuelError>

    /** Retrieve blob data as [Source] stream (postponing data being loaded) */
    suspend fun blobStream(repository: Repository, digest: Digest): Result<Source, FuelError>

    /** Initiates an upload session, returning the [UploadSession] containing a session id and upload location */
    suspend fun initiateUpload(repository: Repository): Result<UploadSession, FuelError>

    /** Finalize upload session. Registry will validate uploaded data against provided [Digest] */
    suspend fun finishBlobUpload(session: UploadSession, digest: Digest): Result<Digest, FuelError>

    /** Uploads a blob chunk (a part of the blob), containing bytes [startRange] to [endRange] (inclusive) */
    suspend fun uploadBlobChunked(
        session: UploadSession,
        buffer: Buffer,
        startRange: Long,
        endRange: Long,
    ): Result<UploadSession, FuelError>

    /** Uploads the whole blob data [Source] as stream */
    suspend fun uploadBlobStream(session: UploadSession, source: Source): Result<UploadSession, FuelError>

    /** Retrieve the status of provided [session], returning the range of already uploaded data (start, end) */
    suspend fun uploadStatus(session: UploadSession): Result<Pair<Long, Long>, FuelError>

    suspend fun cancelBlobUpload(session: UploadSession): Result<*, FuelError>
}
