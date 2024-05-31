package de.cmdjulian.kirc.exception

import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.spec.ErrorResponse
import java.net.URL

sealed class RegistryClientException(
    val url: URL,
    val repository: Repository?,
    val reference: Reference?,
    override val message: String,
    throwable: Throwable? = null,
) : RuntimeException(message, throwable) {

    sealed class ClientException(
        url: URL,
        repository: Repository?,
        reference: Reference?,
        message: String,
        val error: ErrorResponse?,
        cause: Throwable,
    ) : RegistryClientException(url, repository, reference, message, cause) {

        class AuthenticationException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: ErrorResponse?,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "authentication required", error, cause) {
            override fun toString(): String = "DistributionClientException.AuthenticationError -> $message"
        }

        class AuthorizationException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: ErrorResponse?,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "authorization required", error, cause) {
            override fun toString(): String = "DistributionClientException.AuthorizationError -> $message"
        }

        class NotFoundException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: ErrorResponse?,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "not found", error, cause) {
            override fun toString(): String = "DistributionClientException.NotFoundException -> $message"
        }

        class MethodNotAllowed(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: ErrorResponse?,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "method not allowed", error, cause) {
            override fun toString() = "ClientException.MethodNotAllowed (is registry delete enabled?) -> $message"
        }

        class UnexpectedErrorException(
            url: URL,
            repository: Repository?,
            reference: Reference?,
            error: ErrorResponse?,
            cause: Throwable,
        ) : ClientException(url, repository, reference, "unknown error returned by server", error, cause) {
            override fun toString(): String = "DistributionClientException.UnexpectedErrorException -> $message"
        }
    }

    class NetworkErrorException(url: URL, repository: Repository?, reference: Reference?, t: Throwable) :
        RegistryClientException(url, repository, reference, "Network error on registry connect", t) {

        override fun toString(): String = "DistributionClientException.NetworkError -> $message"
    }

    class UnknownErrorException(url: URL, repository: Repository?, reference: Reference?, t: Throwable) :
        RegistryClientException(url, repository, reference, "An unknown error occurred", t) {

        override fun toString(): String = "DistributionClientException.UnknownError -> $message"
    }
}
