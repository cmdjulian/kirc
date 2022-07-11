package oci.distribution.client

import oci.distribution.client.model.domain.RegistryCredentials
import oci.distribution.client.model.domain.Repository
import oci.distribution.client.model.domain.Tag
import java.net.URL
import kotlin.system.exitProcess

fun main() {
    onPrem()
    dockerHub()

    exitProcess(0)
}

private fun onPrem() {
    val creds = RegistryCredentials("changeMe", "changeMe")
    val client = DistributionClientFactory.create(URL("http://localhost:5000"), creds)

    val repository = client.repositories().getOrThrow().first()
    println(repository)

    val tag = client.tags(repository).getOrThrow().first()
    println(tag)

    val manifest = client.manifest(repository, tag).getOrThrow()
    println(manifest)

    val config = client.imageConfig(repository, tag).getOrThrow()
    println(config)
}

fun dockerHub() {
    val client = DistributionClientFactory.create(URL("https://registry-1.docker.io"))

    val repository = Repository("library/python")
    val tag = client.tags(repository).getOrThrow().last()
    println(tag)

    val manifest = client.manifest(repository, Tag("latest")).getOrThrow()
    println(manifest)

    val config = client.imageConfig(repository, Tag("latest")).getOrThrow()
    println(config)
}
