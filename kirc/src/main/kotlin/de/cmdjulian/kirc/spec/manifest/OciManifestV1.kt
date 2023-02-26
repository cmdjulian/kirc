package de.cmdjulian.kirc.spec.manifest

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
