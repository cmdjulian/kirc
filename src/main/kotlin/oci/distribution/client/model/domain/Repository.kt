package oci.distribution.client.model.domain

@JvmInline
value class Repository(private val value: String) {
    override fun toString(): String = value

    fun toString(reference: Reference) = "$this${reference.separator}$reference"
}
