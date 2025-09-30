package de.cmdjulian.kirc.impl

import io.ktor.http.HttpMethod
import io.ktor.http.Url
import java.net.URL

/** Simple HTTP error abstraction replacing FuelError. */
class KtorHttpError(
    val statusCode: Int,
    url: Url,
    val method: HttpMethod,
    val body: ByteArray,
    cause: Throwable? = null,
) : Exception("HTTP $statusCode $method $url", cause) {
    val url = URL(url.toString())
}

