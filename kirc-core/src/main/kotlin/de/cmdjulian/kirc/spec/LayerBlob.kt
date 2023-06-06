package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Digest

class LayerBlob(val digest: Digest, val mediaType: String, val data: ByteArray) {
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is LayerBlob -> false
        mediaType != other.mediaType -> false
        digest != other.digest -> false
        else -> data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = mediaType.hashCode()
        result = 31 * result + digest.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toString(): String = "Blob(digest=$digest, mediaType='$mediaType', size=${data.size})"
}
