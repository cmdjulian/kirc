package de.cmdjulian.distribution.spec.manifest

import com.fasterxml.jackson.annotation.JsonCreator
import de.cmdjulian.distribution.model.Digest

data class LayerReference(val mediaType: String, val size: UInt, val digest: Digest) {
    @JsonCreator
    fun create(mediaType: String, size: UInt, digest: String) = LayerReference(mediaType, size, Digest(digest))
}
