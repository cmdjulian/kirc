package de.cmdjulian.distribution.model.oci

import de.cmdjulian.distribution.model.image.ImageConfigV1
import de.cmdjulian.distribution.model.manifest.docker.ManifestV2

data class DockerImage(val manifest: ManifestV2, val config: ImageConfigV1, val blobs: List<Blob>)
