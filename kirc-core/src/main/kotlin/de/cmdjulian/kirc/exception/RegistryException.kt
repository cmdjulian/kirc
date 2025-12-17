package de.cmdjulian.kirc.exception

import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.spec.RegistryErrorResponse
import java.net.URL

/**
 * Base exception for all errors that can occur when interacting with a container registry via Api.
 */
sealed class RegistryException(
    val url: URL,
    val repository: Repository?,
    val reference: Reference?,
    override val message: String,
    throwable: Throwable? = null,
) : RuntimeException(message, throwable) {

    /** Exception for network related errors, e.g. no connection, timeout, etc. */
    class NetworkErrorException(url: URL, repository: Repository?, reference: Reference?, cause: Throwable) :
        RegistryException(
            url,
            repository,
            reference,
            "Network error on registry connect: ${cause.message}",
            cause,
        ) {

        override fun toString() = "RegistryException.NetworkError -> $message"
    }

    /** Exception for missing headers in the response which are necessary, e.g. digest, byte range. */
    class HeaderMissingException(
        url: URL,
        repository: Repository?,
        reference: Reference?,
        message: String,
        cause: Throwable,
    ) : RegistryException(url, repository, reference, "Invalid response header: $message", cause) {

        override fun toString() = "RegistryException.HeaderMissing -> $message"
    }

    class JsonParsingException(
        url: URL,
        repository: Repository?,
        reference: Reference?,
        message: String,
        cause: Throwable,
    ) : RegistryException(url, repository, reference, "Error parsing response: $message", cause) {

        override fun toString() = "RegistryException.JsonParsingException -> $message"
    }

    /** Base exception for error codes returned by registry. */
    sealed class ClientException(
        url: URL,
        repository: Repository?,
        reference: Reference?,
        message: String,
        val error: RegistryErrorResponse?,
        cause: Throwable,
    ) : RegistryException(url, repository, reference, message, cause) {

        /** Error when the request was malformed. */
        class BadRequestException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse?,
            message: String,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "Bad request: $message", error, cause) {
            override fun toString() = "ClientException.BadRequestError -> $message"
        }

        /** Error when the client is not authenticated. */
        class AuthenticationException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse?,
            message: String,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "Authentication required: $message", error, cause) {
            override fun toString() = "ClientException.AuthenticationError -> $message"
        }

        /** Error when the client is not authorized to access the resource. */
        class AuthorizationException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse?,
            message: String,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "Authorization required: $message", error, cause) {
            override fun toString() = "ClientException.AuthorizationError -> $message"
        }

        /** Error when the requested resource is not found. */
        class NotFoundException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse?,
            message: String,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "Not Found: $message", error, cause) {
            override fun toString() = "ClientException.NotFoundException -> $message"
        }

        /** Error when the method is not allowed, e.g. DELETE on a registry that does not support it. */
        class MethodNotAllowed(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse?,
            message: String,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "Method not allowed: $message", error, cause) {
            override fun toString() = "ClientException.MethodNotAllowed (is registry delete enabled?) -> $message"
        }

        /** Error when the server cannot accept provided chunk (range of bytes). */
        class RangeNotSatisfiable(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse?,
            message: String,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "Requested range not satisfiable: $message", error, cause) {
            override fun toString() = "ClientException.RangeNotSatisfiable -> $message"
        }

        /** Error when the client attempts to contact a service too many times. */
        class TooManyRequests(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse?,
            message: String,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "Too many requests: $message", error, cause) {
            override fun toString() = "ClientException.TooManyRequests -> $message"
        }

        /** Error when the server encountered an unexpected condition, e.g. error not covered yet. */
        class UnexpectedErrorException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse?,
            message: String,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "Unknown error returned by server: $message", error, cause) {
            override fun toString() = "ClientException.UnexpectedErrorException -> $message"
        }
    }
}
