package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.RegistryCredentials
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.utility.MountableFile.forClasspathResource
import java.util.concurrent.TimeUnit

class RegistryTestContainer() : GenericContainer<RegistryTestContainer>("registry:2") {

    init {
        withNetworkAliases("localhost")
        withExposedPorts(5000)
        withCopyFileToContainer(forClasspathResource("htpasswd"), "/auth/htpasswd")

        withEnv("REGISTRY_AUTH", "htpasswd")
        withEnv("REGISTRY_AUTH_HTPASSWD_PATH", "/auth/htpasswd")
        withEnv("REGISTRY_AUTH_HTPASSWD_REALM", "Registry Realm")
        withEnv("REGISTRY_HTTP_ADDR", "0.0.0.0:5000")
        withEnv("REGISTRY_HTTP_SECRET", "shared")
        withEnv("REGISTRY_STORAGE_DELETE_ENABLED", "${true}")
        withEnv("REGISTRY_HTTP_RELATIVEURLS", "${true}")

        val dockerDirectory = "/var/lib/registry"
        withEnv("REGISTRY_STORAGE_FILESYSTEM_ROOTDIRECTORY", dockerDirectory)
        withFileSystemBind("/tmp/testcontainers/registry", dockerDirectory, BindMode.READ_WRITE)

        waitingFor(HostPortWaitStrategy().forPorts(5000))
    }

    val addressHttp by lazy { "http://${host}:${getMappedPort(5000)}" }
    val addressName by lazy { "$host:${getMappedPort(5000)}" }

    fun login(credentials: RegistryCredentials) =
        runDockerCli("echo -n ${credentials.username}:${credentials.password} | base64 | docker login -u ${credentials.username} --password-stdin $addressName")

    fun logout() = runDockerCli("docker logout $addressName")

    fun loadImage(path: String) = runDockerCli("docker load -i $path")

    fun pushImage(image: String, repository: String) {
        val source = image
        val target = "$addressName/$repository/$image"
        runDockerCli("docker image tag $source $target")
        runDockerCli("docker image push $target")
    }

    private fun runDockerCli(command: String) =
        ProcessBuilder(command.split(" ")).inheritIO().start().waitFor(5, TimeUnit.SECONDS)
}
