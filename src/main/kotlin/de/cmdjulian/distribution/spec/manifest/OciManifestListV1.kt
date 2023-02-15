package de.cmdjulian.distribution.spec.manifest

/*
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
data class OciManifestListV1(
    override val schemaVersion: UByte,
    override val mediaType: String?,
    override val manifests: List<ManifestListEntry>,
    val annotations: Map<String, String>,
) : ManifestList {
    companion object {
        const val MediaType = "application/vnd.oci.image.index.v1+json"
    }
}
