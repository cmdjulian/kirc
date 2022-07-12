package oci.distribution.client.model.exception

class InvalidResponseException(throwable: Throwable) : Exception("Registry gave invalid json response", throwable)
