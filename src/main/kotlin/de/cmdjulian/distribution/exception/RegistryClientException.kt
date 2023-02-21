package de.cmdjulian.distribution.exception

sealed class RegistryClientException(override val message: String, throwable: Throwable? = null) :
    RuntimeException(message, throwable) {

    @Suppress("MemberVisibilityCanBePrivate")
    sealed class ClientException(message: String, val error: Error?, cause: Throwable) :
        RegistryClientException(message, cause) {

        class AuthenticationException(error: Error?, cause: Throwable) :
            ClientException("authentication required", error, cause) {
            override fun toString(): String = "DistributionClientException.AuthenticationError -> $message"
        }

        class AuthorizationException(error: Error?, cause: Throwable) :
            ClientException("authorization required", error, cause) {
            override fun toString(): String = "DistributionClientException.AuthorizationError -> $message"
        }

        class NotFoundException(error: Error?, cause: Throwable) : ClientException("not found", error, cause) {
            override fun toString(): String = "DistributionClientException.NotFoundException -> $message"
        }

        class UnexpectedErrorException(error: Error?, cause: Throwable) :
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
