package oci.distribution.client.model.response

import com.fasterxml.jackson.annotation.JsonCreator
import oci.distribution.client.model.domain.Repository

internal data class Catalog(val repositories: List<Repository>) {

    companion object {
        @JvmStatic
        @JsonCreator
        fun create(repositories: List<String>) = Catalog(repositories.map(::Repository))
    }
}
