package de.cmdjulian.kirc

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.utility.MountableFile.forClasspathResource

class RegistryTestContainer() : GenericContainer<RegistryTestContainer>("registry:2") {

    private val REGISTRY_DATA_FOLDER = "/var/lib/registry"

    init {
        //SystemFileSystem.createDirectories(HOST_REGISTRY_DIRECTORY.let(::Path))

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

        withEnv("REGISTRY_STORAGE_FILESYSTEM_ROOTDIRECTORY", REGISTRY_DATA_FOLDER)
        //withFileSystemBind(HOST_REGISTRY_DIRECTORY, dockerDirectory, BindMode.READ_WRITE)
        withTmpFs(mapOf(REGISTRY_DATA_FOLDER to "rw"))

        waitingFor(HostPortWaitStrategy().forPorts(5000))
    }

    val addressHttp by lazy { "http://${host}:${getMappedPort(5000)}" }
    val addressName by lazy { "$host:${getMappedPort(5000)}" }
}
