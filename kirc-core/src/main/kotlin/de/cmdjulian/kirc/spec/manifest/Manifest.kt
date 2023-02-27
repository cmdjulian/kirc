package de.cmdjulian.kirc.spec.manifest

sealed interface Manifest {
    val schemaVersion: Byte
    val mediaType: String?
}

sealed interface ManifestSingle : Manifest {
    val config: LayerReference
    val layers: List<LayerReference>
}

sealed interface ManifestList : Manifest {
    val manifests: List<ManifestListEntry>
}
