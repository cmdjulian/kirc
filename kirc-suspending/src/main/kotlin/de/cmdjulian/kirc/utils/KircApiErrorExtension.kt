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
import de.cmdjulian.kirc.exception.RegistryException.JsonParsingException
import de.cmdjulian.kirc.exception.RegistryException.NetworkErrorException
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.KircApiError
import de.cmdjulian.kirc.spec.RegistryErrorResponse

internal fun KircApiError.toRegistryClientError(
    repository: Repository? = null,
    reference: Reference? = null,
): RegistryException = when (this) {
    is KircApiError.Network -> NetworkErrorException(url, repository, reference, cause)
    is KircApiError.Registry -> toHttpException(repository, reference, body)
    is KircApiError.Header -> HeaderMissingException(url, repository, reference, detailMessage, this)
    is KircApiError.Json -> JsonParsingException(url, repository, reference, detailMessage, this)
    is KircApiError.Bearer -> toHttpException(repository, reference, null)
    is KircApiError.Unknown -> UnexpectedErrorException(url, repository, reference, null, message, this)
}

private fun KircApiError.toHttpException(
    repository: Repository?,
    reference: Reference?,
    body: RegistryErrorResponse?,
): RegistryException.ClientException = when (statusCode) {
    400 -> BadRequestException(url, repository, reference, body, detailMessage, this)
    401 -> AuthenticationException(url, repository, reference, body, detailMessage, this)
    403 -> AuthorizationException(url, repository, reference, body, detailMessage, this)
    404 -> NotFoundException(url, repository, reference, body, detailMessage, this)
    405 -> MethodNotAllowed(url, repository, reference, body, detailMessage, this)
    416 -> RangeNotSatisfiable(url, repository, reference, body, detailMessage, this)
    429 -> TooManyRequests(url, repository, reference, body, detailMessage, this)
    else -> UnexpectedErrorException(url, repository, reference, body, detailMessage, this)
}
