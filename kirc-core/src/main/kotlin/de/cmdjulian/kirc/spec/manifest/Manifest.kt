package de.cmdjulian.kirc.spec.manifest

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
sealed interface Manifest {
    val schemaVersion: Byte
    val mediaType: String?
}

@ReflectionHint
sealed interface ManifestSingle : Manifest {
    val config: LayerReference
    val layers: List<LayerReference>
}

@ReflectionHint
sealed interface ManifestList : Manifest {
    val manifests: List<ManifestListEntry>
}
