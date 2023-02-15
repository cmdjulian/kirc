package de.cmdjulian.distribution.spec.manifest

sealed interface ManifestList {
    val schemaVersion: UByte
    val mediaType: String?
    val manifests: List<ManifestListEntry>
}
