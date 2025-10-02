package de.cmdjulian.kirc

import java.net.URL

/**
 * Exception encountered during processing request internally
 */
sealed class KircRuntimeException(message: String, throwable: Throwable? = null) :
    RuntimeException(message, throwable)

/**
 * Thrown when extracting fields in responses from api calls was unsuccessful.
 *
 * This can either mean that the docker registry api changed or the request wasn't executed properly.
 *
 * Appears while manually extracting fields from responses (no deserialization)
 */
class KircApiException(field: String, url: URL, method: String) :
    KircRuntimeException("Could not determine '$field' from response $method=$url")

/**
 * Encountered an unexpected error during download.
 */
class KircDownloadException(message: String) : KircRuntimeException(message)

/**
 * Encountered an error during upload. Reasons can include:
 * - unexpected errors, empty fields which shouldn't be empty
 * - file metadata couldn't be determined
 * - index file or oci-layout file missing
 */
class KircUploadException(message: String) : KircRuntimeException(message)
