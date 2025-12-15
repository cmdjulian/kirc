package de.cmdjulian.kirc.client

/**
 * Determines the way image blobs are uploaded.
 *
 * Currently supported modes:
 * - [Stream]: uploads blob as stream in one request
 * - [Chunked]: splits blob into [Chunked.chunkSize] bytes and uploads them one after another
 * - [Compatibility]: uploads blob split into 8KB chunks. This is very slow but in some cases works best. Use Chunked instead.
 */
sealed class UploadMode {
    data object Stream : UploadMode()

    @Deprecated("Use Chunked and specify the chunk size")
    data object Compatibility : UploadMode()
    data class Chunked(val chunkSize: Long) : UploadMode()
}
