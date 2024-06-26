package de.cmdjulian.kirc.image

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
@JvmDefaultWithoutCompatibility
sealed interface Reference {
    val separator: Char
    fun asImagePart() = "$separator${toString()}"
}

@ReflectionHint
class Tag(@JsonValue private val value: String) : Reference, Comparable<Tag> {
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

@ReflectionHint
class Digest(@JsonValue private val value: String) : Reference, Comparable<Digest> {
    init {
        require(value.matches(Regex("sha256:[\\da-fA-F]{32,}"))) { "invalid digest" }
    }

    override val separator: Char get() = Companion.separator

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
