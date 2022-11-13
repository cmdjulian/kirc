package de.cmdjulian.distribution.model.oci

@JvmInline
value class Tag(private val value: String) : Reference {

    init {
        require(value.matches(Regex("\\w[\\w.\\-]{0,127}"))) { "invalid tag" }
    }

    override val separator: Char get() = ':'

    override fun toString(): String = value
}
