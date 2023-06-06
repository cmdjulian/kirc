package de.cmdjulian.kirc.image

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.nio.file.Path
import java.util.Locale

class Repository(@JsonValue private val value: String) {
    init {
        require(!Path.of(value).isAbsolute) { "invalid repository, has to be relative" }
        require(value == value.lowercase(Locale.getDefault())) {
            "invalid repository, can only contain lower case chars"
        }
    }

    operator fun plus(reference: Reference): ContainerImageName = when (reference) {
        is Tag -> ContainerImageName(repository = this, tag = reference)
        is Digest -> ContainerImageName(repository = this, digest = reference)
    }

    override fun equals(other: Any?): Boolean = other is Repository && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    // deserializer
    companion object {
        @JvmStatic
        @JsonCreator
        fun of(value: String) = Repository(value)
    }
}
