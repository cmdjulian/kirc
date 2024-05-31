package de.cmdjulian.kirc.spec

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
data class ErrorResponse(val errors: List<Error>)

@ReflectionHint
data class Error(val code: String?, val message: String?, val detail: String?)
