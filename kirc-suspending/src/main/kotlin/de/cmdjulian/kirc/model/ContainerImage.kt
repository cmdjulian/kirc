package de.cmdjulian.kirc.model

import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestSingle

data class ContainerImage(val manifest: ManifestSingle, val config: ImageConfig, val blobs: List<LayerBlob>)
