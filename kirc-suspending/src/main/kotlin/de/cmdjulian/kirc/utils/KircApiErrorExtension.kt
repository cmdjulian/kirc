package de.cmdjulian.kirc.utils

import de.cmdjulian.kirc.exception.RegistryException
import de.cmdjulian.kirc.exception.RegistryException.ClientException.AuthenticationException
import de.cmdjulian.kirc.exception.RegistryException.ClientException.AuthorizationException
import de.cmdjulian.kirc.exception.RegistryException.ClientException.BadRequestException
import de.cmdjulian.kirc.exception.RegistryException.ClientException.MethodNotAllowed
import de.cmdjulian.kirc.exception.RegistryException.ClientException.NotFoundException
import de.cmdjulian.kirc.exception.RegistryException.ClientException.RangeNotSatisfiable
import de.cmdjulian.kirc.exception.RegistryException.ClientException.TooManyRequests
import de.cmdjulian.kirc.exception.RegistryException.ClientException.UnexpectedErrorException
import de.cmdjulian.kirc.exception.RegistryException.HeaderMissingException
import de.cmdjulian.kirc.exception.RegistryException.NetworkErrorException
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.KircApiError

internal fun KircApiError.toRegistryClientError(
    repository: Repository? = null,
    reference: Reference? = null,
): RegistryException = when (this) {
    is KircApiError.Network -> NetworkErrorException(url, repository, reference, cause)
    is KircApiError.Registry -> toHttpException(repository, reference)
    is KircApiError.Header -> HeaderMissingException(url, repository, reference, detailMessage)
}

private fun KircApiError.Registry.toHttpException(
    repository: Repository?,
    reference: Reference?,
): RegistryException = when (statusCode) {
    400 -> BadRequestException(url, repository, reference, body, detailMessage)
    401 -> AuthenticationException(url, repository, reference, body, detailMessage)
    403 -> AuthorizationException(url, repository, reference, body, detailMessage)
    404 -> NotFoundException(url, repository, reference, body, detailMessage)
    405 -> MethodNotAllowed(url, repository, reference, body, detailMessage)
    416 -> RangeNotSatisfiable(url, repository, reference, body, detailMessage)
    429 -> TooManyRequests(url, repository, reference, body, detailMessage)
    else -> UnexpectedErrorException(url, repository, reference, body, detailMessage)
}
