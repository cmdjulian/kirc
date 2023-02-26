package de.cmdjulian.kirc.spec.manifest

// https://docs.docker.com/registry/spec/manifest-v2-2/#manifest-list-field-descriptions
data class DockerManifestListV1(
    override val schemaVersion: UByte,
    override val mediaType: String,
    override val manifests: List<ManifestListEntry>,
) : ManifestList {
    companion object {
        const val MediaType = "application/vnd.docker.distribution.manifest.list.v2+json"
    }
}
