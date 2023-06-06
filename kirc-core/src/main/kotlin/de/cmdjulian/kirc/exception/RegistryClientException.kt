package de.cmdjulian.kirc.exception

sealed class RegistryClientException(override val message: String, throwable: Throwable? = null) :
    RuntimeException(message, throwable) {

    sealed class ClientException(message: String, val error: ErrorResponse?, cause: Throwable) :
        RegistryClientException(message, cause) {

        class AuthenticationException(error: ErrorResponse?, cause: Throwable) :
            ClientException("authentication required", error, cause) {
            override fun toString(): String = "DistributionClientException.AuthenticationError -> $message"
        }

        class AuthorizationException(error: ErrorResponse?, cause: Throwable) :
            ClientException("authorization required", error, cause) {
            override fun toString(): String = "DistributionClientException.AuthorizationError -> $message"
        }

        class NotFoundException(error: ErrorResponse?, cause: Throwable) : ClientException("not found", error, cause) {
            override fun toString(): String = "DistributionClientException.NotFoundException -> $message"
        }

        class MethodNotAllowed(
            error: ErrorResponse?,
            cause: Throwable,
        ) : ClientException("method not allowed", error, cause) {
            override fun toString() = "ClientException.MethodNotAllowed (is registry delete enabled?) -> $message"
        }

        class UnexpectedErrorException(error: ErrorResponse?, cause: Throwable) :
            ClientException("unknown error returned by server", error, cause) {
            override fun toString(): String = "DistributionClientException.UnexpectedErrorException -> $message"
        }
    }

    class NetworkErrorException(t: Throwable) : RegistryClientException("Network error on registry connect", t) {
        override fun toString(): String = "DistributionClientException.NetworkError -> $message"
    }

    class UnknownErrorException(t: Throwable) : RegistryClientException("An unknown error occurred", t) {
        override fun toString(): String = "DistributionClientException.UnknownError -> $message"
    }
}
