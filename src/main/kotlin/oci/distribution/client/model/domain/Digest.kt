package oci.distribution.client.model.domain

@JvmInline
value class Digest(private val value: String) : Reference {
    override val separator: Char
        get() = '@'

    override fun toString(): String = value
}
