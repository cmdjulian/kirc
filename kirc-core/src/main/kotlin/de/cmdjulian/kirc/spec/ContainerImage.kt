package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
data class ContainerImage(val manifest: ManifestSingle, val config: ImageConfig, val blobs: List<LayerBlob>)
