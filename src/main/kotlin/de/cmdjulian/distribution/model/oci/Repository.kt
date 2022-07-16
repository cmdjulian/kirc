package de.cmdjulian.distribution.model.oci

import java.nio.file.Path
import java.util.Locale

@JvmInline
value class Repository(private val value: String) {

    init {
        require(value == value.lowercase(Locale.getDefault())) { "invalid repository, can only contain lower case chars" }
        require(!Path.of(value).isAbsolute) { "invalid repository, has to be relative" }
    }

    operator fun plus(reference: Reference) = value + reference.separator + reference.toString()

    override fun toString(): String = value
}
