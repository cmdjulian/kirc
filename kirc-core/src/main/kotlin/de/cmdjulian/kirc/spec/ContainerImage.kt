package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

/**
 * Represents a Docker Container Image
 */
@ReflectionHint
data class ContainerImage(
    val manifest: ManifestSingle,
    val digest: Digest,
    val config: ImageConfig,
    val blobs: List<LayerBlob>,
)
