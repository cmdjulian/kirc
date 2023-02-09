package de.cmdjulian.distribution.model.manifest.docker

import de.cmdjulian.distribution.model.manifest.BlobReference

/**
 * {
 *     "schemaVersion": 2,
 *     "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
 *     "config": {
 *         "mediaType": "application/vnd.docker.container.image.v1+json",
 *         "digest": "sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7",
 *         "size": 7023
 *     },
 *     "layers": [
 *         {
 *             "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
 *             "digest": "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
 *             "size": 32654
 *         },
 *         {
 *             "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
 *             "digest": "sha256:3c3a4604a545cdc127456d94e421cd355bca5b528f4a9c1905b15da2eb4a4c6b",
 *             "size": 16724
 *         },
 *         {
 *             "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
 *             "digest": "sha256:ec4b8955958665577945c89419d1af06b5f7636b4ac3da7f12184802ad867736",
 *             "size": 73109
 *         }
 *     ]
 * }
 */
data class ManifestV2(
    val schemaVersion: UByte,
    val mediaType: String,
    val config: BlobReference,
    val layers: List<BlobReference>
) {
    companion object {
        const val MediaType = "application/vnd.docker.distribution.manifest.v2+json"
    }
}
