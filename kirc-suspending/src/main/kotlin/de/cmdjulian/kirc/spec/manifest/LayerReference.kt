package de.cmdjulian.kirc.spec.manifest

import de.cmdjulian.kirc.model.Digest

data class LayerReference(val mediaType: String, val size: UInt, val digest: Digest)
