package de.cmdjulian.kirc.spec

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonValue
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import java.util.*

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

        fun current(): Platform {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
            val osArch = System.getProperty("os.arch").lowercase(Locale.getDefault())

            // 1. Map JVM OS name to Library OS Enum
            // Note: If running on Mac, we usually want the LINUX image for containers
            val detectedOs = when {
                "win" in osName -> OS.WINDOWS
                // Treat Mac as Linux for the purpose of pulling container images
                // (since Docker on Mac runs Linux containers)
                "nux" in osName || "mac" in osName || "darwin" in osName -> OS.LINUX
                else -> OS.UNKNOWN
            }

            // 2. Map JVM Arch to Library Architecture Enum
            val detectedArch = when {
                osArch == "amd64" || osArch == "x86_64" -> Architecture.AMD64
                osArch == "aarch64" || osArch == "arm64" -> Architecture.ARM64
                osArch == "i386" -> Architecture.I386
                "riscv" in osArch -> Architecture.RISCV64
                "ppc" in osArch -> Architecture.PPC64LE
                "s390" in osArch -> Architecture.S390X
                else -> Architecture.UNKNOWN
            }

            return Platform(detectedOs, detectedArch)
        }
    }

    override fun toString() = "$os/$arch"
}

@ReflectionHint
enum class OS(@get:JsonValue val string: String) {
    LINUX("linux"),
    WINDOWS("windows"),

    @JsonEnumDefaultValue
    UNKNOWN("unknown"),
    ;

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
    UNKNOWN("unknown"),
    ;

    override fun toString() = string
}
