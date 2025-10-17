package de.cmdjulian.kirc.spec

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

/** Represents an error response from the registry API. */
@ReflectionHint
data class RegistryErrorResponse(val errors: List<RegistryError>)

/** Represents a single error from the registry API. */
@ReflectionHint
data class RegistryError(val code: String, val message: String, val detail: String?)
