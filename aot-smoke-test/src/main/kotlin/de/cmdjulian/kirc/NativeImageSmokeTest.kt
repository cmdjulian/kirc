package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.BlockingClientFactory
import de.cmdjulian.kirc.client.BlockingContainerImageRegistryClient
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import java.net.URL

object NativeImageSmokeTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val kanikoClient = BlockingClientFactory.create(
            credentials = RegistryCredentials("changeMe", "changeMe"),
        )
        val kaniko = ContainerImageName.parse("cmdjulian/kaniko:v1.8.1")
        extracted(kanikoClient, kaniko)

        val mopyClient = BlockingClientFactory.create(
            url = URL("http://localhost:5000"),
            credentials = RegistryCredentials("changeMe", "changeMe"),
        )
        val mopy = ContainerImageName.parse(
            "localhost:5000/cmdjulian/mopy@sha256:21e5a3a316aae3eb8d46dfd9bd61d065f0e92914d304e3bb4c1bf282cc6bc1c3",
        )
        extracted(mopyClient, mopy)
        mopyClient.repositories(1, 1).also(::println)
        mopyClient.repositories().also(::println)
        mopyClient.repositories(limit = 1).also(::println)
        mopyClient.repositories(last = 1).also(::println)

        val client = BlockingClientFactory.create()
        client.manifest(ContainerImageName.parse("library/busybox:latest")).also(::println)
        client.manifest(ContainerImageName.parse("cmdjulian/mopy:latest")).also(::println)
    }

    private fun extracted(client: BlockingContainerImageRegistryClient, image: ContainerImageName) {
        try {
            // test
            client.testConnection()

            // tags
            client.tags(image.repository).also(::println)
            client.tags(image.repository, 1).also(::println)

            // exists
            client.exists(image).also(::println)
            client.exists(image.repository, image.reference).also(::println)

            // digest
            image.tag?.let { client.manifestDigest(image.repository, it).also(::println) }
            val digest = client.manifestDigest(image).also(::println)

            // manifest
            val manifest: ManifestSingle =
                when (val manifest = client.manifest(image.repository, digest).also(::println)) {
                    is ManifestSingle -> manifest
                    is ManifestList -> client.manifest(
                        image.repository,
                        manifest.manifests.first().digest,
                    ) as ManifestSingle
                }

            // config
            client.config(image).also(::println)
            client.config(image.repository, image.reference).also(::println)

            // blob
            client.blob(image.repository, manifest.layers.first().digest).also(::println)

            // image client
            client.toImageClient(image, manifest).also(::println)
            val imageClient = client.toImageClient(image)
            imageClient.tags().also(::println)
            imageClient.manifest().also(::println)
            imageClient.config().also(::println)
            imageClient.blobs().also(::println)
            imageClient.size().also(::println)
            imageClient.toImage().also(::println)
        } catch (e: Exception) {
            throw e
        }
    }
}
