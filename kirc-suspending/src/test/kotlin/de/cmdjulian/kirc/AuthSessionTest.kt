package de.cmdjulian.kirc

import de.cmdjulian.kirc.impl.auth.currentSession
import de.cmdjulian.kirc.impl.auth.withAuthSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class AuthSessionTest {

    @Test
    fun `same auth session has same session ids`() = runTest {
        withAuthSession {
            currentSession() shouldBe currentSession()
        }
    }

    @Test
    fun `different auth sessions have different session ids`() = runTest {
        val firstSessionId = withAuthSession {
            currentSession()
        }
        val secondSessionId = withAuthSession {
            currentSession()
        }
        firstSessionId shouldNotBe secondSessionId
    }

    @Test
    fun `nested auth sessions have same session ids`() = runTest {
        withAuthSession {
            val outerSessionId = currentSession()
            withAuthSession {
                val innerSessionId = currentSession()
                innerSessionId shouldBe outerSessionId
            }
        }
    }
}