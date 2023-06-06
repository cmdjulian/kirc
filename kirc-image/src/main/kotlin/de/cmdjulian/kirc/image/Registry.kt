package de.cmdjulian.kirc.image

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

class Registry(@JsonValue private val value: String) {
    //language=RegExp
    companion object {
        private const val IP_ADDRESS = "(((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4})"
        private const val DOMAIN = "(([a-zA-Z\\d_\\-]+)((\\.[a-zA-Z\\d_\\-]+)*))"
        private const val PORT =
            "(:(6553[0-5]|655[0-2][0-9]\\d|65[0-4](\\d){2}|6[0-4](\\d){3}|[1-5](\\d){4}|[1-9](\\d){0,3}))"
        val regex = Regex("^($IP_ADDRESS|$DOMAIN)$PORT?\$")

        // deserializer
        @JvmStatic
        @JsonCreator
        fun of(value: String) = Registry(value)
    }

    init {
        require(value.matches(regex)) { "invalid registry" }
    }

    override fun equals(other: Any?): Boolean = other is Registry && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value
}
