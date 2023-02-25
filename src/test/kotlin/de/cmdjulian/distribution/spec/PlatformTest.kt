package de.cmdjulian.distribution.spec

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class PlatformTest {
    @Test
    fun `parse platform string`() {
        Platform.parse("linux/amd64") shouldBe Platform(OS.LINUX, Architecture.AMD64)
    }
}