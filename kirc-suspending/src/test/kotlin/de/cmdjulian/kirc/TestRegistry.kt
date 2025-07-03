package de.cmdjulian.kirc

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.utility.MountableFile.forClasspathResource

class TestRegistry(image: String = "registry:2") : GenericContainer<TestRegistry>(image) {

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
        withEnv("REGISTRY_STORAGE_FILESYSTEM_ROOTDIRECTORY", "/var/lib/registry")

        //withCommand("docker run registry")
        waitingFor(HostPortWaitStrategy().forPorts(5000))
    }

    val address by lazy { "http://${host}:${getMappedPort(5000)}" }
}
