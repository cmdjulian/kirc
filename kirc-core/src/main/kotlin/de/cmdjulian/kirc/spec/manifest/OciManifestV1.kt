package de.cmdjulian.kirc.spec.manifest

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

// https://github.com/opencontainers/image-spec/blob/main/manifest.md
@ReflectionHint
data class OciManifestV1(
    override val schemaVersion: Byte,
    override val mediaType: String?,
    override val config: LayerReference,
    override val layers: List<LayerReference>,
    val subject: LayerReference?,
    val annotations: Map<String, String>,
) : ManifestSingle {
    @ReflectionHint
    companion object {
        const val MediaType = "application/vnd.oci.image.manifest.v1+json"
    }
}
