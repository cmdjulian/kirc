package de.cmdjulian.kirc.exception

data class ErrorResponse(val errors: List<Error>)

data class Error(val code: String?, val message: String?, val detail: String?)
