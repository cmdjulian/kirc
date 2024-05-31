package de.cmdjulian.kirc.spec.manifest

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.Architecture
import de.cmdjulian.kirc.spec.OS
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
data class ManifestListEntry(val mediaType: String, val digest: Digest, val size: Long, val platform: Platform?) {
    data class Platform(val os: OS, val architecture: Architecture, val features: List<String>)
}
