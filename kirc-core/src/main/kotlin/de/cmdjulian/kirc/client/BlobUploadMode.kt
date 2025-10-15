package de.cmdjulian.kirc.client

sealed class BlobUploadMode {
    data object Stream : BlobUploadMode()
    data class Chunks(val chunkSize: Long = 10 * 1048576L) : BlobUploadMode()
}