package oci.distribution.client.model.manifest

import oci.distribution.client.model.domain.Digest

data class Config(val mediaType: String, val size: Int, val digest: Digest)
