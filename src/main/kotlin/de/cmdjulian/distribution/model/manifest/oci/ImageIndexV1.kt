package de.cmdjulian.distribution.model.manifest.oci

import com.fasterxml.jackson.annotation.JsonCreator
import de.cmdjulian.distribution.model.oci.Digest

/**
 * {
 *   "schemaVersion": 2,
 *   "mediaType": "application/vnd.oci.image.index.v1+json",
 *   "manifests": [
 *     {
 *       "mediaType": "application/vnd.oci.image.manifest.v1+json",
 *       "size": 7143,
 *       "digest": "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
 *       "platform": {
 *         "architecture": "ppc64le",
 *         "os": "linux"
 *       }
 *     },
 *     {
 *       "mediaType": "application/vnd.oci.image.manifest.v1+json",
 *       "size": 7682,
 *       "digest": "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270",
 *       "platform": {
 *         "architecture": "amd64",
 *         "os": "linux"
 *       }
 *     }
 *   ],
 *   "annotations": {
 *     "com.example.key1": "value1",
 *     "com.example.key2": "value2"
 *   }
 * }
 */
// https://github.com/opencontainers/image-spec/blob/main/image-index.md
data class ImageIndexV1(
    val schemaVersion: UByte,
    val mediaType: String,
    val manifests: List<ManifestEntry>,
    val annotations: Map<String, String> = emptyMap()
) {

    companion object {
        const val MediaType = "application/vnd.oci.image.index.v1+json"
    }

    data class ManifestEntry(val mediaType: String, val digest: Digest, val size: Short, val platform: Platform?) {
        data class Platform(val architecture: String, val os: String, val features: List<String>)

        @JsonCreator
        constructor(mediaType: String, digest: String, size: Short, platform: Platform) :
            this(mediaType, Digest(digest), size, platform)
    }
}
