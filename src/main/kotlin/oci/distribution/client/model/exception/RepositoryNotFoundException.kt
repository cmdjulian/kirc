package oci.distribution.client.model.exception

import oci.distribution.client.model.domain.Repository

class RepositoryNotFoundException(repository: Repository) : NotFoundException("Repository", repository.toString())
