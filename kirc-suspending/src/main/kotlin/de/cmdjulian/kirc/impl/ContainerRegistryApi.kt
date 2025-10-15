package de.cmdjulian.kirc.impl

import com.github.kittinunf.result.Result
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.response.Catalog
import de.cmdjulian.kirc.impl.response.ResultSource
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

    suspend fun ping(): Result<*, KircApiError>
    suspend fun repositories(limit: Int?, last: Int?): Result<Catalog, KircApiError>
    suspend fun tags(repository: Repository, limit: Int?, last: Int?): Result<TagList, KircApiError>
    suspend fun digest(repository: Repository, reference: Reference): Result<Digest, KircApiError>

    // Manifest

    suspend fun existsManifest(repository: Repository, reference: Reference, accept: String): Result<*, KircApiError>

    /** Retrieve certain (single) image manfiest */
    suspend fun manifest(repository: Repository, reference: Reference): Result<ManifestSingle, KircApiError>

    /** Retrieve any image manifest matching [reference] */
    suspend fun manifests(repository: Repository, reference: Reference): Result<Manifest, KircApiError>

    /**
     * Retrieve any image manifest matching [reference] as data stream
     */
    suspend fun manifestStream(repository: Repository, reference: Reference): Result<ResultSource, KircApiError>

    suspend fun uploadManifest(
        repository: Repository,
        reference: Reference,
        manifest: Manifest,
    ): Result<Digest, KircApiError>

    suspend fun deleteManifest(repository: Repository, reference: Reference): Result<Digest, KircApiError>

    // Blob

    suspend fun existsBlob(repository: Repository, digest: Digest): Result<*, KircApiError>

    /** Retrieve blob data as [ByteArray] (loaded into memory) */
    suspend fun blob(repository: Repository, digest: Digest): Result<ByteArray, KircApiError>

    /** Retrieve blob data as [Source] stream (postponing data being loaded) */
    suspend fun blobStream(repository: Repository, digest: Digest): Result<Source, KircApiError>

    /** Initiates an upload session, returning the [UploadSession] containing a session id and upload location */
    suspend fun initiateUpload(repository: Repository): Result<UploadSession, KircApiError>

    /** Finalize upload session. Registry will validate uploaded data against provided [Digest] */
    suspend fun finishBlobUpload(session: UploadSession, digest: Digest): Result<Digest, KircApiError>

    /** Uploads a blob chunk (a part of the blob), containing bytes [startRange] to [endRange] (inclusive) */
    suspend fun uploadBlobChunked(
        session: UploadSession,
        buffer: Buffer,
        startRange: Long,
        endRange: Long,
    ): Result<UploadSession, KircApiError>

    /** Uploads the whole blob data [Source] as stream */
    suspend fun uploadBlobStream(
        session: UploadSession,
        digest: Digest,
        stream: RequestBodyType.Stream,
        size: Long,
    ): Result<Digest, KircApiError>

    /** Retrieve the status of provided [session], returning the range of already uploaded data (start, end) */
    suspend fun uploadStatus(session: UploadSession): Result<Pair<Long, Long>, KircApiError>

    suspend fun cancelBlobUpload(session: UploadSession): Result<*, KircApiError>
}
