package de.cmdjulian.distribution.spec.manifest

sealed interface Manifest {
    val schemaVersion: UByte
    val mediaType: String?
    val config: LayerReference
    val layers: List<LayerReference>
}
