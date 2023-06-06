package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle

private suspend fun extracted(client: SuspendingContainerImageRegistryClient, image: ContainerImageName) {
    try {
        // test
        client.testConnection()

        // tags
        client.tags(image.repository).also(::println)
        client.tags(image.repository, 1).also(::println)

        // exists
        client.exists(image.repository, image.reference).also(::println)

        // digest
        val digest = client.manifestDigest(image.repository, image.reference).also(::println)

        // manifest
        val manifest: ManifestSingle = when (val manifest = client.manifest(image.repository, digest).also(::println)) {
            is ManifestSingle -> manifest
            is ManifestList -> client.manifest(image.repository, manifest.manifests.first().digest) as ManifestSingle
        }

        // config
        client.config(image.repository, image.reference).also(::println)

        // blob
        client.blob(image.repository, manifest.layers.first().digest).also(::println)

        // image client
        val imageClient = client.toImageClient(image.repository, image.reference, manifest)
        imageClient.tags().also(::println)
        imageClient.manifest().also(::println)
        imageClient.config().also(::println)
        imageClient.blobs().also(::println)
        imageClient.size().also(::println)
        imageClient.toImage().also(::println)

        client.manifestDelete(image.repository, image.reference)
    } catch (e: Exception) {
        throw e
    }
}
