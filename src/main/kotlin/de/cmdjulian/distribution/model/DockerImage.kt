package de.cmdjulian.distribution.model

import de.cmdjulian.distribution.spec.image.docker.ImageV1
import de.cmdjulian.distribution.spec.manifest.DockerManifestV2

data class DockerImage(val manifest: DockerManifestV2, val config: ImageV1, val blobs: List<Blob>)
