package oci.distribution.client.model.exception

data class ErrorResponse(val errors: List<Error>)

data class Error(val code: String, val detail: Any?, val message: String)
