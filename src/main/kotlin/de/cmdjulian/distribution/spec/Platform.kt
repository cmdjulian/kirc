package de.cmdjulian.distribution.spec

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.module.kotlin.readValue
import de.cmdjulian.distribution.impl.JsonMapper

private val platformRegex = Regex(
    "^(${Architecture.values().map(Architecture::string).joinToString(separator = "|")})" +
        "/" +
        "(${OS.values().map(OS::string).joinToString(separator = "|")})$",
)

data class Platform(val os: OS, val arch: Architecture) {
    companion object {
        fun parse(platform: String): Platform {
            when {
                !platform.matches(Regex("^([a-z0-9]+)/([a-z0-9/]+)\$")) ->
                    throw IllegalArgumentException("invalid platform string")

                !platform.matches(Regex("^(${OS.values().joinToString(separator = "|") { "$it" }})/([a-z0-9/]+)")) ->
                    throw IllegalArgumentException("invalid os part")

                !platform.matches(Regex("^([a-z0-9]+)/(${Architecture.values().joinToString(separator = "|") { "$it" }})")) ->
                    throw IllegalArgumentException("invalid architecture part")
            }
            val (os, arch) = platform.split(Regex("/"), 2)

            return Platform(JsonMapper.readValue("\"$os\""), JsonMapper.readValue("\"$arch\""))
        }
    }

    override fun toString() = "$os/$arch"
}

enum class OS(@get:JsonValue val string: String) {
    WINDOWS("windows"), LINUX("linux");

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
    ARM_V6("arm/v6");

    override fun toString() = string
}