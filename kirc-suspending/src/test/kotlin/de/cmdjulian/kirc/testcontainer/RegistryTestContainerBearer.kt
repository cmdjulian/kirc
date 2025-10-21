package de.cmdjulian.kirc.testcontainer

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile

class RegistryTestContainerBearer(network: Network, authServiceUrl: String) :
    GenericContainer<RegistryTestContainerBearer>("registry:2") {

    private val registryDataFolder = "/var/lib/registry"

    init {
        withNetwork(network)
        withNetworkAliases("registry")
        withExposedPorts(5000)
        withCopyFileToContainer(MountableFile.forClasspathResource("cert.pem"), "/auth/cert.pem")

        // auth
        withEnv("REGISTRY_AUTH_TOKEN_REALM", "$authServiceUrl/auth")
        withEnv("REGISTRY_AUTH_TOKEN_SERVICE", "registry")
        withEnv("REGISTRY_AUTH_TOKEN_ISSUER", "auth_service")
        withEnv("REGISTRY_AUTH_TOKEN_ROOTCERTBUNDLE", "auth/cert.pem")
        // http
        withEnv("REGISTRY_HTTP_ADDR", "0.0.0.0:5000")
        withEnv("REGISTRY_HTTP_SECRET", "shared")
        withEnv("REGISTRY_STORAGE_DELETE_ENABLED", "${true}")
        withEnv("REGISTRY_HTTP_RELATIVEURLS", "${true}")
        withEnv("REGISTRY_LOG_LEVEL", "debug")

        withEnv("REGISTRY_STORAGE_FILESYSTEM_ROOTDIRECTORY", registryDataFolder)
        withTmpFs(mapOf(registryDataFolder to "rw"))

        waitingFor(Wait.forListeningPort())
    }

    val addressHttp by lazy { "http://$host:${getMappedPort(5000)}" }
}