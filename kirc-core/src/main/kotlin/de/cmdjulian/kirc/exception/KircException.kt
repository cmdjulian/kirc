package de.cmdjulian.kirc.exception

import de.cmdjulian.kirc.spec.Platform

/**
 * Exception encountered during processing request internally in kirc
 */
sealed class KircException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    /**
     * Encountered an error during upload. Reasons can include:
     * - unexpected errors, empty fields which shouldn't be empty
     * - file metadata couldn't be determined
     * - index file or oci-layout file missing
     */
    class CorruptArchiveError(message: String, cause: Throwable? = null) :
        KircException("Corrupt archive: $message", cause) {
        override fun toString() = "KircException.CorruptArchive -> $message"
    }

    /** Encountered an invalid state. This usually indicates a bug in kirc. */
    class InvalidState(message: String) : KircException("Invalid State $message") {
        override fun toString() = "KircException.InvalidState -> $message"
    }

    class UnexpectedError(message: String, cause: Throwable) : KircException(message, cause) {
        override fun toString() = "KircException.UnexpectedError -> $message"
    }

    /** Thrown */
    class PlatformNotMatching(current: Platform) :
        KircException("No manifest with config found where platform is matching current platform: $current") {
        override fun toString() = "KircException.PlatformNotMatching -> $message"
    }
}
