@file:Suppress("ktlint:trailing-comma-on-declaration-site")

package de.cmdjulian.distribution.spec

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonValue

data class Platform(val os: OS, val arch: Architecture) {
    companion object {
        private val platform = Regex("^([a-z0-9]+)/([a-z0-9]+)(/([a-z0-9]+))?\$")

        fun parse(platform: String): Platform {
            if (!platform.matches(this.platform)) throw IllegalArgumentException("invalid platform string")
            val (osString, archString) = platform.split(Regex("/"), 2)
            val os = OS.values().firstOrNull { os -> os.string == osString } ?: OS.UNKNOWN
            val arch = Architecture.values().firstOrNull { arch -> arch.string == archString } ?: Architecture.UNKNOWN

            return Platform(os, arch)
        }
    }

    override fun toString() = "$os/$arch"
}

enum class OS(@get:JsonValue val string: String) {
    LINUX("linux"),
    WINDOWS("windows"),

    @JsonEnumDefaultValue
    UNKNOWN("unknown");

    override fun toString() = string
}

enum class Architecture(@get:JsonValue val string: String) {
    AMD64("amd64"),
    ARM64("arm64"),
    RISCV64("riscv64"),
    PPC64LE("ppc64le"),
    S390X("s390x"),
    I386("386"),
    ARM_V7("arm/v7"),
    ARM_V6("arm/v6"),
    ARM_V5("arm/v5"),

    @JsonEnumDefaultValue
    UNKNOWN("unknown");

    override fun toString() = string
}
