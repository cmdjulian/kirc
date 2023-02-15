package de.cmdjulian.distribution.impl

import com.haroldadmin.cnradapter.NetworkResponse
import de.cmdjulian.distribution.exception.DistributionError
import de.cmdjulian.distribution.exception.DistributionError.ClientErrorException
import de.cmdjulian.distribution.exception.ErrorResponse
import retrofit2.Response
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

@JvmName("toResultIgnoreError")
internal fun <S, T> NetworkResponse<S, Unit>.toResult(block: (S, Response<*>) -> T): Result<T> {
    return when (this) {
        is NetworkResponse.Success -> Result.success(block(body, response))
        is NetworkResponse.NetworkError -> Result.failure(DistributionError.NetworkError(error))
        is NetworkResponse.UnknownError -> Result.failure(DistributionError.UnknownError(error))
        is NetworkResponse.ServerError -> when (code!!) {
            HTTP_UNAUTHORIZED -> Result.failure(ClientErrorException.AuthenticationError(null))
            HTTP_FORBIDDEN -> Result.failure(ClientErrorException.AuthorizationError(null))
            HTTP_NOT_FOUND -> Result.failure(ClientErrorException.NotFoundException(null))
            else -> Result.failure(ClientErrorException.UnexpectedClientErrorException(null))
        }
    }
}

@JvmName("toResultIgnoreError")
internal fun <S> NetworkResponse<S, Unit>.toResult(): Result<S> = toResult { body, _ -> body }

@JvmName("toResult")
internal fun <S, U> NetworkResponse<S, ErrorResponse>.toResult(block: (S, Response<*>) -> U): Result<U> {
    return when (this) {
        is NetworkResponse.Success -> runCatching { block(body, response) }
        is NetworkResponse.NetworkError -> Result.failure(DistributionError.NetworkError(error))
        is NetworkResponse.UnknownError -> Result.failure(DistributionError.UnknownError(error))
        is NetworkResponse.ServerError -> when (code!!) {
            HTTP_UNAUTHORIZED -> Result.failure(ClientErrorException.AuthenticationError(body?.errors?.firstOrNull()))
            HTTP_FORBIDDEN -> Result.failure(ClientErrorException.AuthorizationError(body?.errors?.firstOrNull()))
            HTTP_NOT_FOUND -> Result.failure(ClientErrorException.NotFoundException(body?.errors?.firstOrNull()))
            else -> Result.failure(ClientErrorException.UnexpectedClientErrorException(body?.errors?.firstOrNull()))
        }
    }
}

@JvmName("toResult")
internal fun <S> NetworkResponse<S, ErrorResponse>.toResult(): Result<S> = toResult { s, _ -> s }
