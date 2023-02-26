package de.cmdjulian.kirc.image

import java.nio.file.Path
import java.util.*

@JvmInline
value class Repository(private val value: String) {
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

    override fun toString(): String = value
}
