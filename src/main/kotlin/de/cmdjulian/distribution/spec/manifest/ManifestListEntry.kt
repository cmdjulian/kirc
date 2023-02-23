package de.cmdjulian.distribution.spec.manifest

import de.cmdjulian.distribution.model.Digest

data class ManifestListEntry(val mediaType: String, val digest: Digest, val size: UInt, val platform: Platform?) {
    data class Platform(val os: String, val architecture: String, val features: List<String>? = null)
}
