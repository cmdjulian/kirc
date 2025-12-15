package de.cmdjulian.kirc.client

sealed class UploadMode {
    data object Stream: UploadMode()
    @Deprecated("Use Chunked and specify the chunk size")
    data object Compatibility: UploadMode()
    data class Chunked(val chunkSize: Long): UploadMode()
}