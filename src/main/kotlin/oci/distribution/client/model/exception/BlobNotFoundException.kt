package oci.distribution.client.model.exception

import oci.distribution.client.model.domain.Digest
import oci.distribution.client.model.domain.Repository

class BlobNotFoundException(repository: Repository, digest: Digest) :
    NotFoundException("Blob", repository + digest)
