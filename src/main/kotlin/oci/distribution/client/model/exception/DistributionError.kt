package oci.distribution.client.model.exception

sealed class DistributionError(message: String, throwable: Throwable? = null) : Exception(message, throwable) {

    sealed class ClientErrorException(message: String, val error: Error?) : DistributionError(message) {
        class AuthenticationError(error: Error?) : ClientErrorException("authentication required", error)
        class AuthorizationError(error: Error?) : ClientErrorException("authorization required", error)
        class NotFoundException(error: Error?) : ClientErrorException("not found", error)
        class UnexpectedClientErrorException(error: Error?) :
            ClientErrorException("unknown error returned by the server", error)

        override fun toString(): String = javaClass.simpleName + '(' + (error?.message ?: message!!) + ')'
    }

    class NetworkError(t: Throwable) : DistributionError("Can't connect to registry, network error", t)

    class UnknownError(t: Throwable) : DistributionError("An unknown error occurred", t)
}
