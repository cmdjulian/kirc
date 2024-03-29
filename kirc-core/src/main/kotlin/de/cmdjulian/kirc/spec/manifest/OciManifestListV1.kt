package de.cmdjulian.kirc.spec.manifest

// https://github.com/opencontainers/image-spec/blob/main/image-index.md
data class OciManifestListV1(
    override val schemaVersion: Byte,
    override val mediaType: String?,
    override val manifests: List<ManifestListEntry>,
    val annotations: Map<String, String>,
) : ManifestList {
    companion object {
        const val MediaType = "application/vnd.oci.image.index.v1+json"
    }
}
