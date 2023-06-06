package de.cmdjulian.kirc.spec.manifest

import de.cmdjulian.kirc.image.Digest

data class LayerReference(val mediaType: String, val size: Long, val digest: Digest)
