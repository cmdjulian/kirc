package de.cmdjulian.kirc.spec

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonValue
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
data class Platform(val os: OS, val arch: Architecture) {
    @ReflectionHint
    companion object {
        private val platform = Regex("^([a-z0-9]+)/([a-z0-9]+)(/([a-z0-9]+))?\$")

        fun parse(platform: String): Platform {
            require(platform.matches(this.platform)) { "invalid platform string" }
            val (osString, archString) = platform.split(Regex("/"), 2)
            val os = OS.entries.firstOrNull { os -> os.string == osString } ?: OS.UNKNOWN
            val arch = Architecture.entries.firstOrNull { arch -> arch.string == archString } ?: Architecture.UNKNOWN

            return Platform(os, arch)
        }
    }

    override fun toString() = "$os/$arch"
}

@ReflectionHint
enum class OS(@get:JsonValue val string: String) {
    LINUX("linux"),
    WINDOWS("windows"),

    @JsonEnumDefaultValue
    UNKNOWN("unknown");

    override fun toString() = string
}

@ReflectionHint
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
