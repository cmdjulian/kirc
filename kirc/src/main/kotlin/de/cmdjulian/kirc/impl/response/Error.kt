@file:Suppress("unused")

package de.cmdjulian.kirc.impl.response

internal data class Errors(val errors: List<Error>)

internal data class Error(val code: Code, val message: String, val details: String)

internal enum class Code {
    BLOB_UNKNOWN,
    BLOB_UPLOAD_INVALID,
    BLOB_UPLOAD_UNKNOWN,
    DIGEST_INVALID,
    MANIFEST_BLOB_UNKNOWN,
    MANIFEST_INVALID,
    MANIFEST_UNKNOWN,
    NAME_INVALID,
    NAME_UNKNOWN,
    SIZE_INVALID,
    UNAUTHORIZED,
    DENIED,
    UNSUPPORTED,
    TOOMANYREQUESTS,
}
