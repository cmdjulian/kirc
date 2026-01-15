package de.cmdjulian.kirc.impl.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// The context element holding the Session ID
data class AuthSession(val id: UUID) : AbstractCoroutineContextElement(AuthSession) {
    companion object Key : CoroutineContext.Key<AuthSession>
}

enum class ScopeType(val value: String) {
    PULL("pull"),
    PUSH("push"),
    PULL_PUSH("pull,push"),
    ALL("*"),
    NONE(""),
}

/**
 * Starts a scope with a shared auth session uuid.
 * The ID is attached to every request made within the scope so that they can reuse bearer authentication tokens
 *
 * - Requests sharing a session, share the same session uuid.
 * - If a session is already present in the context, it is reused.
 * - Different sessions contain different session ids.
 */
suspend fun <T> withAuthSession(block: suspend CoroutineScope.() -> T): T {
    // Check if we already have a session in the current context
    if (currentCoroutineContext()[AuthSession] != null) {
        // Reuse existing session, just maintaining structured concurrency
        return coroutineScope(block)
    }

    // Create new session
    return withContext(AuthSession(UUID.randomUUID())) {
        block()
    }
}

// Helper to retrieve the implicit ID or generate a new standalone one
internal suspend fun currentSession(): UUID = currentCoroutineContext()[AuthSession]?.id ?: UUID.randomUUID()
