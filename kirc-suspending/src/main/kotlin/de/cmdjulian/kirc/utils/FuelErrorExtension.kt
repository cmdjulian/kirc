package de.cmdjulian.kirc.utils

import com.fasterxml.jackson.module.kotlin.readValue
import de.cmdjulian.kirc.exception.RegistryClientException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.AuthenticationException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.AuthorizationException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.BadRequestException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.MethodNotAllowed
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.NotFoundException
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.RangeNotSatisfiable
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.TooManyRequests
import de.cmdjulian.kirc.exception.RegistryClientException.ClientException.UnexpectedErrorException
import de.cmdjulian.kirc.exception.RegistryClientException.NetworkErrorException
import de.cmdjulian.kirc.exception.RegistryClientException.UnknownErrorException
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.JsonMapper
import de.cmdjulian.kirc.impl.KtorHttpError

internal fun KtorHttpError.toRegistryClientError(
    repository: Repository? = null,
    reference: Reference? = null,
): RegistryClientException {
    val data = body
    return when (statusCode) {
        -1 -> NetworkErrorException(url, repository, reference, cause)
        400 -> BadRequestException(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, cause)
        401 -> AuthenticationException(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, cause)
        403 -> AuthorizationException(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, cause)
        404 -> NotFoundException(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, cause)
        405 -> MethodNotAllowed(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, cause)
        416 -> RangeNotSatisfiable(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, cause)
        429 -> TooManyRequests(url, repository, reference, tryOrNull { JsonMapper.readValue(data) }, cause)
        in 406..499 -> UnexpectedErrorException(
            url,
            repository,
            reference,
            tryOrNull { JsonMapper.readValue(data) },
            cause,
        )

        else -> UnknownErrorException(url, repository, reference, cause)
    }
}

private inline fun <T> tryOrNull(block: () -> T): T? = runCatching(block).getOrNull()
