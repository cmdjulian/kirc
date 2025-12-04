package de.cmdjulian.kirc.spec.manifest

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

// https://github.com/opencontainers/image-spec/blob/main/image-index.md
@ReflectionHint
data class OciManifestListV1(
    override val schemaVersion: Byte,
    override val mediaType: String?,
    override val manifests: MutableList<ManifestListEntry>,
    val annotations: Map<String, String>,
) : ManifestList {
    @ReflectionHint
    @Suppress("ktlint:standard:property-naming")
    companion object {
        const val MediaType = "application/vnd.oci.image.index.v1+json"
    }
}
