package de.cmdjulian.distribution.model

sealed interface Reference {
    val separator: Char
}

@JvmInline
value class Tag(private val value: String) : Reference {
    init {
        require(value.matches(Regex("\\w[\\w.\\-]{0,127}"))) { "invalid tag" }
    }

    override val separator: Char get() = ':'

    override fun toString(): String = value
}

@JvmInline
value class Digest(private val value: String) : Reference, Comparable<Digest> {
    init {
        require(value.matches(Regex("sha256:[\\da-fA-F]{32,}"))) { "invalid digest" }
    }

    override val separator: Char get() = '@'

    override fun compareTo(other: Digest): Int = value.compareTo(other.value)

    override fun toString(): String = value
}
