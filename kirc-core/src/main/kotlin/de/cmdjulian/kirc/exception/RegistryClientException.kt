package de.cmdjulian.kirc.exception

import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.spec.RegistryErrorResponse
import java.net.URL

/**
 * Base exception for all errors that can occur when interacting with a container registry via Api.
 */
sealed class RegistryClientException(
    val url: URL,
    val repository: Repository?,
    val reference: Reference?,
    override val message: String,
    throwable: Throwable? = null,
) : RuntimeException(message, throwable) {

    /** Exception for network related errors, e.g. no connection, timeout, etc. */
    class NetworkErrorException(url: URL, repository: Repository?, reference: Reference?, cause: Throwable) :
        RegistryClientException(
            url,
            repository,
            reference,
            "Network error on registry connect: ${cause.message}",
            cause,
        ) {

        override fun toString() = "RegistryClientException.NetworkError -> $message"
    }

    /** Exception for missing headers in the response which are necessary, e.g. digest, byte range. */
    class HeaderMissingException(url: URL, repository: Repository?, reference: Reference?, message: String) :
        RegistryClientException(url, repository, reference, "Invalid response header: $message") {

        override fun toString() = "RegistryClientException.HeaderMissing -> $message"
    }

    /** Base exception for error codes returned by registry. */
    sealed class ClientException(
        url: URL,
        repository: Repository?,
        reference: Reference?,
        message: String,
        val error: RegistryErrorResponse,
    ) : RegistryClientException(url, repository, reference, message, null) {

        /** Error when the request was malformed. */
        class BadRequestException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse,
            message: String,
        ) : ClientException(url, repository, reference, "Bad request: $message", error) {
            override fun toString() = "ClientException.BadRequestError -> $message"
        }

        /** Error when the client is not authenticated. */
        class AuthenticationException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse,
            message: String,
        ) : ClientException(url, repository, reference, "Authentication required: $message", error) {
            override fun toString() = "ClientException.AuthenticationError -> $message"
        }

        /** Error when the client is not authorized to access the resource. */
        class AuthorizationException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse,
            message: String,
        ) : ClientException(url, repository, reference, "Authorization required: $message", error) {
            override fun toString() = "ClientException.AuthorizationError -> $message"
        }

        /** Error when the requested resource is not found. */
        class NotFoundException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse,
            message: String,
        ) : ClientException(url, repository, reference, "Not Found: $message", error) {
            override fun toString() = "ClientException.NotFoundException -> $message"
        }

        /** Error when the method is not allowed, e.g. DELETE on a registry that does not support it. */
        class MethodNotAllowed(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse,
            message: String,
        ) : ClientException(url, repository, reference, "Method not allowed: $message", error) {
            override fun toString() = "ClientException.MethodNotAllowed (is registry delete enabled?) -> $message"
        }

        /** Error when the server cannot accept provided chunk (range of bytes). */
        class RangeNotSatisfiable(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse,
            message: String,
        ) : ClientException(url, repository, reference, "Requested range not satisfiable: $message", error) {
            override fun toString() = "ClientException.RangeNotSatisfiable -> $message"
        }

        /** Error when the client attempts to contact a service too many times. */
        class TooManyRequests(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse,
            message: String,
        ) : ClientException(url, repository, reference, "Too many requests: $message", error) {
            override fun toString() = "ClientException.TooManyRequests -> $message"
        }

        /** Error when the server encountered an unexpected condition, e.g. error not covered yet. */
        class UnexpectedErrorException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: RegistryErrorResponse,
            message: String,
        ) : ClientException(url, repository, reference, "Unknown error returned by server: $message", error) {
            override fun toString() = "ClientException.UnexpectedErrorException -> $message"
        }
    }
}
