package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Digest

class LayerBlob(val digest: Digest, val mediaType: String, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBlob) return false

        if (mediaType != other.mediaType) return false
        if (digest != other.digest) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mediaType.hashCode()
        result = 31 * result + digest.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toString(): String = "Blob(digest=$digest, mediaType='$mediaType', size=${data.size})"
}
