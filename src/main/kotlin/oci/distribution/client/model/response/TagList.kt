package oci.distribution.client.model.response

import com.fasterxml.jackson.annotation.JsonCreator
import oci.distribution.client.model.domain.Tag

internal data class TagList(val name: String, val tags: List<Tag>) {
    companion object {
        @JvmStatic
        @JsonCreator
        fun create(name: String, tags: List<String>) = TagList(name, tags.map(::Tag))
    }
}
