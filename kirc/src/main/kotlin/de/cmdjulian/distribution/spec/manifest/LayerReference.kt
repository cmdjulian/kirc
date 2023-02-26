package de.cmdjulian.distribution.spec.manifest

import de.cmdjulian.distribution.model.Digest

data class LayerReference(val mediaType: String, val size: UInt, val digest: Digest)
