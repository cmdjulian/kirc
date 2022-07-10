package oci.distribution.client

import oci.distribution.client.model.domain.RegistryCredentials
import java.net.URL

fun main() {
    val creds = RegistryCredentials("changeMe", "changeMe")
    val client = DistributionClientFactory.create(URL("http://localhost:5000"), creds)

    client.testConnection()
}
