package de.cmdjulian.distribution.spec.manifest

import de.cmdjulian.distribution.model.Digest
import de.cmdjulian.distribution.spec.Architecture
import de.cmdjulian.distribution.spec.OS

data class ManifestListEntry(val mediaType: String, val digest: Digest, val size: UInt, val platform: Platform?) {
    data class Platform(val os: OS, val architecture: Architecture, val features: List<String>? = null)
}
