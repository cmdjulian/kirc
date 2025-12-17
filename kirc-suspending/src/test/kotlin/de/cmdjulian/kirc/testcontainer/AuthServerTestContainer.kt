package de.cmdjulian.kirc.testcontainer

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile

class AuthServerTestContainer(
    network: Network,
) : GenericContainer<AuthServerTestContainer>("cesanta/docker_auth:1") {

    init {
        withNetwork(network)
        withNetworkAliases("auth")
        withExposedPorts(5001)
        withCopyFileToContainer(MountableFile.forClasspathResource("auth_config.yml"), "/config/auth_config.yml")
        withCopyFileToContainer(MountableFile.forClasspathResource("cert.pem"), "/config/cert.pem")
        withCopyFileToContainer(MountableFile.forClasspathResource("cert.key"), "/config/cert.key")
        withCommand("/config/auth_config.yml")
        waitingFor(Wait.forListeningPort())
    }

    val addressAuth by lazy { "http://$host:${getMappedPort(5001)}" }
}
