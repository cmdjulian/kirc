package de.cmdjulian.kirc.spec.manifest

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

/**
 * Represents a Docker manifest
 *
 * Can either be a [ManifestSingle] or [ManifestList]
 */
@ReflectionHint
sealed interface Manifest {
    val schemaVersion: Byte
    val mediaType: String?
}

/**
 * Represents a single manifest
 *
 * A single manifest is information about an image, such as layers, size, and digest.
 */
@ReflectionHint
sealed interface ManifestSingle : Manifest {
    val config: LayerReference
    val layers: List<LayerReference>
}

/**
 * Represents a manifest list
 *
 * A manifest list is a list of image layers that is created by specifying one or more
 *  (ideally more than one) image names
 *
 * Ideally, a manifest list is created from images that are identical in function for different os/arch combinations.
 */
@ReflectionHint
sealed interface ManifestList : Manifest {
    val manifests: MutableList<ManifestListEntry>
}
