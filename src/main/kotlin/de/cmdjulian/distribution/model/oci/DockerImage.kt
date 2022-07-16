package de.cmdjulian.distribution.model.oci

import de.cmdjulian.distribution.model.image.ImageConfig
import de.cmdjulian.distribution.model.manifest.ManifestV2
import de.cmdjulian.distribution.model.oci.Blob

data class DockerImage(val manifest: ManifestV2, val config: ImageConfig, val blobs: List<Blob>)
