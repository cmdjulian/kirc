package de.cmdjulian.distribution.spec.manifest

/*
 * {
 *   "schemaVersion": 2,
 *   "mediaType": "application/vnd.oci.image.manifest.v1+json",
 *   "config": {
 *     "mediaType": "application/vnd.oci.image.config.v1+json",
 *     "size": 7023,
 *     "digest": "sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7"
 *   },
 *   "layers": [
 *     {
 *       "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
 *       "size": 32654,
 *       "digest": "sha256:9834876dcfb05cb167a5c24953eba58c4ac89b1adf57f28f2f9d09af107ee8f0"
 *     },
 *     {
 *       "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
 *       "size": 16724,
 *       "digest": "sha256:3c3a4604a545cdc127456d94e421cd355bca5b528f4a9c1905b15da2eb4a4c6b"
 *     },
 *     {
 *       "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
 *       "size": 73109,
 *       "digest": "sha256:ec4b8955958665577945c89419d1af06b5f7636b4ac3da7f12184802ad867736"
 *     }
 *   ],
 *   "subject": {
 *     "mediaType": "application/vnd.oci.image.manifest.v1+json",
 *     "size": 7682,
 *     "digest": "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270"
 *   },
 *   "annotations": {
 *     "com.example.key1": "value1",
 *     "com.example.key2": "value2"
 *   }
 * }
 */
// https://github.com/opencontainers/image-spec/blob/main/manifest.md
data class OciManifestV1(
    override val schemaVersion: UByte,
    override val mediaType: String?,
    override val config: LayerReference,
    override val layers: List<LayerReference>,
    val subject: LayerReference?,
    val annotations: Map<String, String>,
) : ManifestSingle {
    companion object {
        const val MediaType = "application/vnd.oci.image.manifest.v1+json"
    }
}
