package oci.distribution.client.model.response

import oci.distribution.client.model.domain.Repository

internal data class Catalog(val repositories: List<Repository>)
