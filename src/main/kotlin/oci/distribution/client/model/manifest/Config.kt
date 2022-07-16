package oci.distribution.client.model.manifest

import com.fasterxml.jackson.annotation.JsonCreator
import oci.distribution.client.model.oci.Digest

data class Config(val mediaType: String, val size: Int, val digest: Digest) {
    companion object {
        @JvmStatic
        @JsonCreator
        fun create(mediaType: String, size: Int, digest: String) = Config(mediaType, size, Digest(digest))
    }
}
