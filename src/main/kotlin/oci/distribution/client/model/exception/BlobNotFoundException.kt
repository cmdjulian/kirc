package oci.distribution.client.model.exception

import oci.distribution.client.model.domain.Digest

class BlobNotFoundException(digest: Digest) : NotFoundException("Blob", digest.value)
