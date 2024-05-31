package de.cmdjulian.kirc.spec.manifest

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

// https://docs.docker.com/registry/spec/manifest-v2-2/#image-manifest
@ReflectionHint
data class DockerManifestV2(
    override val schemaVersion: Byte,
    override val mediaType: String,
    override val config: LayerReference,
    override val layers: List<LayerReference>,
) : ManifestSingle {
    @ReflectionHint
    @Suppress("ktlint:standard:property-naming")
    companion object {
        const val MediaType = "application/vnd.docker.distribution.manifest.v2+json"
    }
}
