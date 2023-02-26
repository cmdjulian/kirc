package de.cmdjulian.kirc.spec.manifest

// https://docs.docker.com/registry/spec/manifest-v2-2/#image-manifest
data class DockerManifestV2(
    override val schemaVersion: UByte,
    override val mediaType: String,
    override val config: LayerReference,
    override val layers: List<LayerReference>,
) : ManifestSingle {
    companion object {
        const val MediaType = "application/vnd.docker.distribution.manifest.v2+json"
    }
}
