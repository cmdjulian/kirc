package oci.distribution.client.model.domain

@JvmInline
value class Tag(override val value: String) : Reference {
    override fun toString(): String = value
}
