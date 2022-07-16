package oci.distribution.client.model.oci

import oci.distribution.client.model.image.ImageConfig
import oci.distribution.client.model.manifest.ManifestV2

data class DockerImage(val manifest: ManifestV2, val config: ImageConfig, val blobs: Map<Digest, ByteArray>)
