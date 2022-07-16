package de.cmdjulian.distribution.model.manifest

import com.fasterxml.jackson.annotation.JsonCreator
import de.cmdjulian.distribution.model.oci.Digest

data class Layer(val mediaType: String, val size: Int, val digest: Digest) {
    companion object {
        @JvmStatic
        @JsonCreator
        fun create(mediaType: String, size: Int, digest: String) = Layer(mediaType, size, Digest(digest))
    }
}
