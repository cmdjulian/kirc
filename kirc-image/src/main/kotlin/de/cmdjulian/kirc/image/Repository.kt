package de.cmdjulian.kirc.image

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.nio.file.Path
import java.util.*

class Repository(@JsonValue private val value: String) : Comparable<Repository> {
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

    override fun compareTo(other: Repository): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is Repository && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    companion object {
        @JvmStatic
        @JsonCreator
        fun of(repository: String) = Repository(repository)
    }
}
