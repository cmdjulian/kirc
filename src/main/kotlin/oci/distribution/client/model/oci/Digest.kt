package oci.distribution.client.model.oci

@JvmInline
value class Digest(private val value: String) : Reference {

    init {
        require(value.matches(Regex("sha256:[\\da-fA-F]{32,}"))) { "invalid digest" }
    }

    override val separator: Char
        get() = '@'

    override fun toString(): String = value
}
