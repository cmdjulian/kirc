package de.cmdjulian.kirc.image

@JvmDefaultWithCompatibility
sealed interface Reference {
    val separator: Char

    fun asImagePart() = "$separator${toString()}"
}

@JvmInline
value class Tag(private val value: String) : Reference {
    init {
        require(value.matches(Regex("\\w[\\w.\\-]{0,127}"))) { "invalid tag" }
    }

    override val separator: Char get() = Companion.separator

    override fun toString(): String = value

    companion object {
        @get:JvmStatic
        @get:JvmName("latest")
        val LATEST = Tag("latest")
        const val separator: Char = ':'
    }
}

@JvmInline
value class Digest(private val value: String) : Reference, Comparable<Digest> {
    init {
        require(value.matches(Regex("sha256:[\\da-fA-F]{32,}"))) { "invalid digest" }
    }

    override val separator: Char get() = Companion.separator

    override fun compareTo(other: Digest): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        const val separator: Char = '@'
    }
}
