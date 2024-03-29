package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.BlockingContainerImageClientFactory
import de.cmdjulian.kirc.client.BlockingContainerImageRegistryClient
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import java.net.URI

object NativeImageSmokeTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val kanikoClient = BlockingContainerImageClientFactory.create(
            credentials = RegistryCredentials("cmdjulian", "changeMe"),
        )
        val kaniko = ContainerImageName.parse("cmdjulian/kaniko:v1.8.1")
        extracted(kanikoClient, kaniko)

        val mopyClient = BlockingContainerImageClientFactory.create(
            url = URI("http://localhost:5000"),
            credentials = RegistryCredentials("changeMe", "changeMe"),
        )
        val mopy = ContainerImageName.parse(
            "localhost:5000/cmdjulian/mopy@sha256:5922f18a229685e881fcb13103e1704c8d932c247e5f3847bae56f14bb2af9a3",
        )
        extracted(mopyClient, mopy)
        mopyClient.repositories(1, 1).also(::println)
        mopyClient.repositories().also(::println)
        mopyClient.repositories(limit = 1).also(::println)
        mopyClient.repositories(last = 1).also(::println)

        val client = BlockingContainerImageClientFactory.create()
        val busybox = ContainerImageName.parse("library/busybox:latest")
        client.manifest(busybox.repository, busybox.reference).also(::println)
        val kanikoLatest = ContainerImageName.parse("cmdjulian/mopy:latest")
        client.manifest(kanikoLatest.repository, kanikoLatest.reference).also(::println)

        mopyClient.manifestDelete(mopy.repository, mopy.reference)
    }

    private fun extracted(client: BlockingContainerImageRegistryClient, image: ContainerImageName) {
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
            val manifest: ManifestSingle =
                when (val manifest = client.manifest(image.repository, digest).also(::println)) {
                    is ManifestSingle -> manifest
                    is ManifestList -> client.manifest(
                        image.repository,
                        manifest.manifests.first().digest,
                    ) as ManifestSingle
                }

            // config
            client.config(image.repository, image.reference).also(::println)

            // blob
            client.blob(image.repository, manifest.layers.first().digest).also(::println)

            // image client
            val imageClient = client.toImageClient(image.repository, image.reference)
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
