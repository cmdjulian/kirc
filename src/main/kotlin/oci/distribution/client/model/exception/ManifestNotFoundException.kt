package oci.distribution.client.model.exception

import oci.distribution.client.model.domain.Reference

class ManifestNotFoundException(reference: Reference) : NotFoundException("Blob", reference.value)
