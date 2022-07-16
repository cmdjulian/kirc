package de.cmdjulian.distribution.model.oci

@JvmInline
value class Digest(private val value: String) : Reference, Comparable<Digest> {

    init {
        require(value.matches(Regex("sha256:[\\da-fA-F]{32,}"))) { "invalid digest" }
    }

    override val separator: Char
        get() = '@'

    override fun compareTo(other: Digest): Int = value.compareTo(other.value)

    override fun toString(): String = value
}
