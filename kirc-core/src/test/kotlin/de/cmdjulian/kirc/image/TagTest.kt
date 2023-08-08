package de.cmdjulian.kirc.image

import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import org.junit.jupiter.api.Test

class TagTest {
    @Test
    fun `should be equal for latest`() {
        Tag.LATEST shouldBeEqualComparingTo Tag.LATEST
    }

    @Test
    fun `should be equal`() {
        Tag("1.2.0") shouldBeEqualComparingTo Tag("1.2.0")
    }

    @Test
    fun `should be smaller`() {
        Tag("1.2") shouldBeLessThan Tag("1.3")
    }

    @Test
    fun `should be bigger`() {
        Tag("1.3") shouldBeGreaterThan Tag("1.2")
    }

    @Test
    fun `latest is always greater`() {
        Tag.LATEST shouldBeGreaterThan Tag("99999999")
    }

    @Test
    fun `nothing is greater than latest`() {
        Tag("99999999") shouldBeLessThan Tag.LATEST
    }
}
