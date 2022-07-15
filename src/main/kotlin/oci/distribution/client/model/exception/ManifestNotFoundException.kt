package oci.distribution.client.model.exception

import oci.distribution.client.model.domain.Reference
import oci.distribution.client.model.domain.Repository

class ManifestNotFoundException(repository: Repository, reference: Reference) :
    NotFoundException("Manifest", repository + reference)
