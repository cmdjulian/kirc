package de.cmdjulian.kirc.client

/**
 * The mode to use for uploading blobs.
 * - [Stream] - upload the blob in a single request. Doesn't load all data into memory at once.
 * - [Chunks] - upload the blob in multiple requests. Loads the provided [Chunks.chunkSize] into memory.
 */
sealed class BlobUploadMode {
    /** Upload the blob in a single request. */
    data object Stream : BlobUploadMode()

    /** Upload the blob in multiple requests */
    data class Chunks(val chunkSize: Long = 10 * 1048576L) : BlobUploadMode()
}
