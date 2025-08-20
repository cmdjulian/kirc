package de.cmdjulian.kirc

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile.forClasspathResource

class RegistryTestContainer() : GenericContainer<RegistryTestContainer>("registry:2") {

    private val REGISTRY_DATA_FOLDER = "/var/lib/registry"

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
        withEnv("REGISTRY_LOG_LEVEL", "debug")

        withEnv("REGISTRY_STORAGE_FILESYSTEM_ROOTDIRECTORY", REGISTRY_DATA_FOLDER)
        withTmpFs(mapOf(REGISTRY_DATA_FOLDER to "rw"))

        waitingFor(Wait.forListeningPort())
    }

    val addressHttp by lazy { "http://${host}:${getMappedPort(5000)}" }
}
