package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.RegistryCredentials
import java.util.concurrent.TimeUnit

object DockerRegistryCliHelper {

    private val images = mutableListOf<String>()
    private val loggedInRegistry = mutableListOf<String>()

    fun login(addressName: String, credentials: RegistryCredentials) =
        runDockerCli("docker login $addressName -u ${credentials.username} -p ${credentials.password}")
            .also { loggedInRegistry.add(addressName) }

    fun loadImage(path: String, imageName: String) = runDockerCli("docker load -i $path")
        .also { images.add(imageName) }

    fun pushImage(addressName: String, repository: String, image: String) {
        val taggedImageName = "$addressName/$repository/$image"
        runDockerCli("docker image tag $image $taggedImageName")
        runDockerCli("docker image push $taggedImageName")
        images.add(taggedImageName)
    }

    fun logoutFromAllTestRegistries() = loggedInRegistry
        .forEach { registry -> runDockerCli("docker logout $registry") }
        .also { loggedInRegistry.clear() }

    fun removeAllTestImages() = images
        .forEach { runDockerCli("docker image rm -f $it") }
        .also { images.clear() }

    private fun runDockerCli(command: String) =
        ProcessBuilder(command.split(" ")).inheritIO().start().waitFor(5, TimeUnit.SECONDS)
}