package oci.distribution.client

import oci.distribution.client.api.DistributionApiFactory
import oci.distribution.client.api.RegistryCredentials
import oci.distribution.client.model.oci.DockerImageSlug
import oci.distribution.client.model.oci.Repository
import oci.distribution.client.model.oci.Tag
import java.net.URL
import kotlin.system.exitProcess

suspend fun main() {
    client()
    dockerfiles()
    onPrem()
    dockerHub()

    exitProcess(0)
}

fun dockerfiles() {
    var i = DockerImageSlug.parse("name")
    i = DockerImageSlug.parse("name:tag")
    i = DockerImageSlug.parse("path/name")
    i = DockerImageSlug.parse("path/name:tag")
    i = DockerImageSlug.parse("some.registry/name")
    i = DockerImageSlug.parse("some.registry/name:tag")
    i = DockerImageSlug.parse("some.registry/path/name")
    i = DockerImageSlug.parse("some.registry/path/name:tag")
    i = DockerImageSlug.parse("some.registry/path/name@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    i = DockerImageSlug.parse("some.registry/path/name:latest@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
}

private suspend fun onPrem() {
    val creds = RegistryCredentials("changeMe", "changeMe")
    val client = DistributionApiFactory.create(URL("http://localhost:5000"), creds)

    val repository = client.repositories().getOrThrow().first()
    println(repository)

    val tag = client.tags(repository).getOrThrow().first()
    println(tag)

    val manifest = client.manifest(repository, tag).getOrThrow()
    println(manifest)

    val config = client.config(repository, tag).getOrThrow()
    println(config)
}

suspend fun dockerHub() {
    val client = DistributionApiFactory.create()

    val repository = Repository("library/python")
    val tag = client.tags(repository).getOrThrow().last()
    println(tag)

    val manifest = client.manifest(repository, Tag("latest")).getOrThrow()
    println(manifest)

    val config = client.config(repository, Tag("latest")).getOrThrow()
    println(config)
}

suspend fun client() {
    val slug = DockerImageSlug.parse("localhost:5000/registry:2")
    val client = DistributionApiFactory.create(
        slug,
        credentials = RegistryCredentials("changeMe", "changeMe"),
        insecure = true
    )

    // exists
    println(client.exists())

    // tags
    val tags = client.tags()
    println(tags.getOrThrow())

    // Manifest
    val manifest = client.manifest()
    println(manifest.getOrThrow())

    // Config
    val config = client.config()
    println(config.getOrThrow())

    // size
    val size = client.size()
    println(size.getOrThrow())

    // Image
    val image = client.toDockerImage()
    println(image.getOrThrow())

    // delete
    client.delete().getOrThrow()
}
