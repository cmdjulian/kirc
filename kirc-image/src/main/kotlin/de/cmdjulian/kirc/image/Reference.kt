package de.cmdjulian.kirc.image

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import de.cmdjulian.kirc.image.Tag.Companion.LATEST
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

/**
 * Used to identify a docker image version
 *
 * Can either be a [Tag] or a [Digest]
 */
@ReflectionHint
@JvmDefaultWithoutCompatibility
sealed interface Reference {
    val separator: Char
    fun asImagePart() = "$separator${toString()}"
}

/**
 * An optional identifier used to specify a particular version or variant of the image.
 * If no tag is provided, docker defaults to [LATEST].
 *
 * *MUTABLE*
 *
 * Example: "repository:latest"
 */
@ReflectionHint
class Tag(@JsonValue private val value: String) :
    Reference,
    Comparable<Tag> {
    init {
        require(value.matches(Regex("\\w[\\w.\\-]{0,127}"))) { "invalid tag" }
    }

    override val separator: Char get() = Companion.separator

    override fun compareTo(other: Tag): Int = when {
        this == LATEST -> if (other == LATEST) 0 else 1
        other == LATEST -> -1
        else -> this.value.compareTo(other.value)
    }

    override fun equals(other: Any?): Boolean = other is Tag && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    @ReflectionHint
    @Suppress("ktlint:standard:property-naming")
    companion object {
        val LATEST = Tag("latest")
        const val separator: Char = ':'

        @JvmStatic
        @JsonCreator
        fun of(tag: String) = Tag(tag)
    }
}

/**
 * A unique identifier for a docker image that is computed based on its content.
 * Unlike a [Tag], which can be updated, it is immutable and can be used to verify the integrity of an image.
 *
 * *IMMUTABLE*
 *
 * Example: "repository@sha256:cbbf2f9a99b47fc460d422812b6a5adff7dfee951d8fa2e4a98caa0382cfbdbf"
 */
@ReflectionHint
class Digest(@JsonValue private val value: String) :
    Reference,
    Comparable<Digest> {
    init {
        require(value.matches(Regex("sha256:[\\da-fA-F]{32,}"))) { "invalid digest" }
    }

    override val separator: Char get() = Companion.separator
    val hash: String get() = value.split(":")[1]

    override fun compareTo(other: Digest): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is Digest && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    @ReflectionHint
    @Suppress("ktlint:standard:property-naming")
    companion object {
        const val separator: Char = '@'

        @JvmStatic
        @JsonCreator
        fun of(digest: String) = Digest(digest)
    }
}
