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
    class Registry(statusCode: Int, url: Url, method: HttpMethod, val body: RegistryErrorResponse) :
        KircApiError(
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
    class Network(url: Url, method: HttpMethod, override val cause: Throwable) :
        KircApiError(-1, url, method, cause, cause.message ?: "Unknown network error") {
        override fun toString(): String = "KircApiError.Network -> ${this@Network.message}"
    }

    /**
     * Thrown when extracting fields in responses from api calls was unsuccessful.
     *
     * This can either mean that the docker registry api changed or the request wasn't executed properly.
     *
     * Appears while manually extracting fields from responses (no deserialization)
     */
    class Header(statusCode: Int, url: Url, method: HttpMethod, override val message: String) :
        KircApiError(statusCode, url, method, null, message) {
        override fun toString(): String = "KircApiError.Header -> ${this@Header.message}"
    }

    /**
     * Thrown when parsing json responses from api calls was unsuccessful.
     *
     * E.g. when deserializing error responses from the registry or the token response from the auth server.
     */
    class Json(statusCode: Int, url: Url, method: HttpMethod, override val cause: Throwable, message: String) :
        KircApiError(statusCode, url, method, cause, message) {
        override fun toString(): String = "KircApiError.Json -> ${this@Json.message}"
    }

    /**
     * Thrown when an authentication bearer token could not be retrieved from the auth server.
     */
    class Bearer(statusCode: Int, url: Url, method: HttpMethod, override val message: String) :
        KircApiError(statusCode, url, method, null, message) {
        override fun toString(): String = "KircApiError.Bearer -> ${this@Bearer.message}"
    }

    /**
     * Unknown error occurred.
     *
     * Should not happen, but is here for completeness.
     */
    class Unknown(override val cause: Throwable) :
        KircApiError(-1, Url("/"), HttpMethod("?"), cause, cause.message ?: "Unknown error") {
        override fun toString(): String = "KircApiError.Unknown -> ${this@Unknown.message}"
    }
}
