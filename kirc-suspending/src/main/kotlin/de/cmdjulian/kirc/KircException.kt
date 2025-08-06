package de.cmdjulian.kirc

import com.github.kittinunf.fuel.core.Method
import java.net.URL

sealed class KircException(override val message: String, throwable: Throwable? = null) :
    RuntimeException(message, throwable)

class KircApiException(field: String, url: URL, method: Method) :
    KircException("Could not determine '$field' from response $method=$url")

class KircInternalError(message: String) : KircException(message)

class KircUploadException(message: String) : KircException(message)