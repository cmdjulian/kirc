package de.cmdjulian.distribution.model

import de.cmdjulian.distribution.spec.image.ImageConfig
import de.cmdjulian.distribution.spec.manifest.ManifestSingle

data class ContainerImage(val manifest: ManifestSingle, val config: ImageConfig, val blobs: List<LayerBlob>)
