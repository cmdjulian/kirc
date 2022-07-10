package oci.distribution.client.model.exception

abstract class NotFoundException(resourceName: String, identifier: String) :
    Exception("$resourceName identified by '$identifier' not found")
