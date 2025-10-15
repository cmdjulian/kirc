package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.spec.RegistryErrorResponse
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import java.net.URL

/** Simple API error abstraction. */
internal sealed class KircApiError(
    val statusCode: Int,
    url: Url,
    val method: HttpMethod,
    cause: Throwable?,
    override val message: String,
) : RuntimeException(message, cause) {
    val url = URL(url.toString())
    val detailMessage = "$message (HTTP statusCode=$statusCode method=$method url=$url)"

    /** Error as returned by the registry. */
    class Registry(
        statusCode: Int,
        url: Url,
        method: HttpMethod,
        val body: RegistryErrorResponse,
    ) : KircApiError(
        statusCode,
        url,
        method,
        null,
        "Registry error: [${
            body.errors.joinToString(
                prefix = "(",
                postfix = ")",
            ) { "code=${it.code}, message=${it.message}, detail=${it.detail}" }
        }]",
    ) {
        override fun toString(): String = "KircApiError.Registry -> ${this@Registry.message}"
    }

    /** Error caused by network issues, e.g. no connection, timeout, etc. */
    class Network(
        url: Url,
        method: HttpMethod,
        override val cause: Throwable,
    ) : KircApiError(-1, url, method, cause, cause.message ?: "Network error: '${cause.message}'") {
        override fun toString(): String = "KircApiError.Network -> ${this@Network.message}"
    }

    /** Error caused by missing headers in the response. */
    class Header(
        statusCode: Int,
        url: Url,
        method: HttpMethod,
        message: String,
    ) : KircApiError(statusCode, url, method, null, message) {
        override fun toString(): String = "KircApiError.Header -> ${this@Header.message}"
    }
}
