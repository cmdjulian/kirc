package de.cmdjulian.distribution.spec.manifest

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
