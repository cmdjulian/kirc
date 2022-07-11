package oci.distribution.client.model.domain

import java.net.URI

data class Proxy(
    val uri: URI,
    val skipTlsVerify: Boolean = false,
    val username: String? = null,
    val password: String? = null
)
