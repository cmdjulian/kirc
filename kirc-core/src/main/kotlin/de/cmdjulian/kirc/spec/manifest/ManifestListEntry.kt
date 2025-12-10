package de.cmdjulian.kirc.spec.manifest

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.Architecture
import de.cmdjulian.kirc.spec.OS
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
data class ManifestListEntry(
    val mediaType: String,
    val digest: Digest,
    val size: Long,
    val platform: Platform?,
    val annotations: Map<String, String>?,
) {
    data class Platform(val os: OS, val architecture: Architecture, val features: List<String>)

    /**
     * Checks whether the platform is unknown (either OS or architecture is UNKNOWN).
     *
     * This is useful to filter out manifests that do not correspond to a real platform,
     * such as attestations or cache manifests.
     */
    fun platformIsUnknown(): Boolean = platform?.os == OS.UNKNOWN || platform?.architecture == Architecture.UNKNOWN
}
