package de.cmdjulian.kirc.spec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

internal class PlatformTest {
    companion object {
        @JvmStatic
        fun `platform string method source`(): List<Arguments> = listOf(
            Arguments.of("linux/amd64", OS.LINUX, Architecture.AMD64),
            Arguments.of("linux/arm64", OS.LINUX, Architecture.ARM64),
            Arguments.of("linux/riscv64", OS.LINUX, Architecture.RISCV64),
            Arguments.of("linux/ppc64le", OS.LINUX, Architecture.PPC64LE),
            Arguments.of("linux/s390x", OS.LINUX, Architecture.S390X),
            Arguments.of("linux/386", OS.LINUX, Architecture.I386),
            Arguments.of("linux/arm/v7", OS.LINUX, Architecture.ARM_V7),
            Arguments.of("linux/arm/v6", OS.LINUX, Architecture.ARM_V6),
            Arguments.of("linux/arm/v5", OS.LINUX, Architecture.ARM_V5),
            Arguments.of("windows/amd64", OS.WINDOWS, Architecture.AMD64),
            Arguments.of("windows/arm64", OS.WINDOWS, Architecture.ARM64),
            Arguments.of("windows/riscv64", OS.WINDOWS, Architecture.RISCV64),
            Arguments.of("windows/ppc64le", OS.WINDOWS, Architecture.PPC64LE),
            Arguments.of("windows/s390x", OS.WINDOWS, Architecture.S390X),
            Arguments.of("windows/386", OS.WINDOWS, Architecture.I386),
            Arguments.of("windows/arm/v7", OS.WINDOWS, Architecture.ARM_V7),
            Arguments.of("windows/arm/v6", OS.WINDOWS, Architecture.ARM_V6),
            Arguments.of("windows/arm/v5", OS.WINDOWS, Architecture.ARM_V5),
        )
    }

    @MethodSource("platform string method source")
    @ParameterizedTest(name = "parse platform string: {0}")
    fun `parse valid platform string`(platformString: String, expectedOs: OS, expectedArchitecture: Architecture) {
        Platform.parse(platformString) shouldBe Platform(expectedOs, expectedArchitecture)
    }

    @Test
    fun `unknown platform string should be parsed as unknown`() {
        Platform.parse("mac/aarch64") shouldBe Platform(OS.UNKNOWN, Architecture.UNKNOWN)
    }

    @ValueSource(strings = ["Mac/arm64", "linux/invalid-arch", "linux/arm//inv"])
    @ParameterizedTest(name = "parse invalid platform string: {0}")
    fun `parse platform string`(platformString: String) {
        shouldThrow<IllegalArgumentException> { Platform.parse(platformString) }
    }
}
