package de.cmdjulian.kirc.impl

import java.net.URL

/** Simple HTTP error abstraction replacing FuelError. */
class KtorHttpError(
    val statusCode: Int,
    url: String,
    val method: String,
    val body: ByteArray,
    cause: Throwable? = null,
) : Exception("HTTP $statusCode $method $url", cause) {
    val url: URL = URL(url)
}

