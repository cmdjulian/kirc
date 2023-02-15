package de.cmdjulian.distribution.spec.manifest

import com.fasterxml.jackson.annotation.JsonCreator
import de.cmdjulian.distribution.model.Digest

data class ManifestListEntry(val mediaType: String, val digest: Digest, val size: Short, val platform: Platform?) {
    data class Platform(val architecture: String, val os: String, val features: List<String>)

    companion object {
        @JvmStatic
        @JsonCreator
        fun create(mediaType: String, digest: String, size: Short, platform: Platform) = ManifestListEntry(
            mediaType,
            Digest(digest),
            size,
            platform,
        )
    }
}
