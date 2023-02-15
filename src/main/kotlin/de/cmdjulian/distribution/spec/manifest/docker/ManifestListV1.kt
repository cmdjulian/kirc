package de.cmdjulian.distribution.spec.manifest.docker

import de.cmdjulian.distribution.spec.manifest.ManifestListEntry

/*
 * {
 *   "schemaVersion": 2,
 *   "mediaType": "application/vnd.docker.distribution.manifest.list.v2+json",
 *   "manifests": [
 *     {
 *       "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
 *       "digest": "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
 *       "size": 7143,
 *       "platform": {
 *         "architecture": "ppc64le",
 *         "os": "linux"
 *       }
 *     },
 *     {
 *       "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
 *       "digest": "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270",
 *       "size": 7682,
 *       "platform": {
 *         "architecture": "amd64",
 *         "os": "linux",
 *         "features": [
 *           "sse4"
 *         ]
 *       }
 *     }
 *   ]
 * }
 */
// https://docs.docker.com/registry/spec/manifest-v2-2/#manifest-list-field-descriptions
data class ManifestListV1(val schemaVersion: UByte, val mediaType: String, val manifests: List<ManifestListEntry>) {
    companion object {
        const val MediaType = "application/vnd.docker.distribution.manifest.list.v2+json"
    }
}
