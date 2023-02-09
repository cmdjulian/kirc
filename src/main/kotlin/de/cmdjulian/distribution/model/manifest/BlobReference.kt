package de.cmdjulian.distribution.model.manifest

import com.fasterxml.jackson.annotation.JsonCreator
import de.cmdjulian.distribution.model.oci.Digest

data class BlobReference(val mediaType: String, val size: UInt, val digest: Digest) {
    @JsonCreator
    constructor(mediaType: String, size: UInt, digest: String) : this(mediaType, size, Digest(digest))
}
