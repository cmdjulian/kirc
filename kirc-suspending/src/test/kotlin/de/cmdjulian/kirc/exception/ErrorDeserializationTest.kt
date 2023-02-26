package de.cmdjulian.kirc.exception

import de.cmdjulian.kirc.spec.unmarshal
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class ErrorDeserializationTest {
    @Test
    fun deserialize() {
        @Language("json")
        val json = """
            {
                "errors": [
                    {
                        "code": "MANIFEST_UNKNOWN",
                        "message": "manifest unknown",
                        "detail": "unknown tag=ttt"
                    }
                ]
            }
        """.trimIndent()

        val response = json.unmarshal<ErrorResponse>()
        response.errors.shouldBeSingleton {
            it.code shouldBe "MANIFEST_UNKNOWN"
            it.message shouldBe "manifest unknown"
            it.detail shouldBe "unknown tag=ttt"
        }
    }
}
