package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.ContainerImageName
import de.cmdjulian.distribution.model.Tag
import de.cmdjulian.distribution.spec.manifest.ManifestList
import de.cmdjulian.distribution.spec.manifest.ManifestSingle

fun main() {
    try {
        val client = ContainerRegistryClientFactory.create().toBlockingClient()

        val image = ContainerImageName.parse("cmdjulian/kaniko:v1.8.1")
        val digest = client.manifestDigest(image.repository, image.reference as Tag).also(::println)

        val manifest = when (val manifest = client.manifest(image.repository, digest).also(::println)) {
            is ManifestSingle -> manifest
            is ManifestList -> client.manifest(image.repository, manifest.manifests.first().digest)
        }

        val imageClient = client.toImageClient(image, manifest as ManifestSingle)
        imageClient.toImage().also(::println)
    } catch (e: Exception) {
        throw e
    }
}
