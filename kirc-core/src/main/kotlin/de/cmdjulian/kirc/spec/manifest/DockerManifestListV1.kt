package de.cmdjulian.kirc.spec.manifest

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

// https://docs.docker.com/registry/spec/manifest-v2-2/#manifest-list-field-descriptions
@ReflectionHint
data class DockerManifestListV1(
    override val schemaVersion: Byte,
    override val mediaType: String,
    override val manifests: List<ManifestListEntry>,
) : ManifestList {
    @ReflectionHint
    companion object {
        const val MediaType = "application/vnd.docker.distribution.manifest.list.v2+json"
    }
}
