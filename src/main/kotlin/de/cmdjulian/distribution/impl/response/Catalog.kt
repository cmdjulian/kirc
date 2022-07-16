package de.cmdjulian.distribution.impl.response

import com.fasterxml.jackson.annotation.JsonCreator
import de.cmdjulian.distribution.model.oci.Repository

internal data class Catalog(val repositories: List<Repository>) {

    companion object {
        @JvmStatic
        @JsonCreator
        fun create(repositories: List<String>?) = Catalog(repositories?.map(::Repository) ?: emptyList())
    }
}
