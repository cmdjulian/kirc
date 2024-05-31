package de.cmdjulian.kirc.spec.manifest

import de.cmdjulian.kirc.image.Digest
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
data class LayerReference(val mediaType: String, val size: Long, val digest: Digest)
