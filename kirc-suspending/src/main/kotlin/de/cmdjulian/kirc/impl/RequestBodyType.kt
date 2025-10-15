package de.cmdjulian.kirc.impl

import io.ktor.utils.io.ByteReadChannel

/**
 * Represents the type of a request body, either binary data or a stream.
 *
 * Important for the [ResponseRetryWithAuthentication] request body handling.
 * Since a request body can only be consumed once, we need to know if we can retry the request
 * with the same body or if we need to recreate the consumable, e.g. [java.io.InputStream] or [kotlinx.io.Source].
 */
sealed interface RequestBodyType {
    /** Represents binary data. */
    object Binary : RequestBodyType

    /**
     * An interface to provide consumable data.
     *
     * The [channel] function is a suspending function that provides a [ByteReadChannel]
     *  which can be handled as stream by Ktor HTTP client.
     *
     * Attention: The [channel] function is called every time the request is retried.
     *  Therefore, the implementation must provide a new [ByteReadChannel] on each call,
     *  i.e. by opening a new [kotlinx.io.Source] or [java.io.InputStream] or [java.io.File].
     */
    fun interface Stream : RequestBodyType {
        suspend fun channel(): ByteReadChannel
    }
}
