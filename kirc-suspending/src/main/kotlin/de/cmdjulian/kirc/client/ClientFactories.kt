package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.model.ContainerImageName
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

const val DOCKER_HUB_URL = "https://registry.hub.docker.com"

class RegistryCredentials(val username: String, val password: String)

interface ContainerImageRegistryClientFactory<T> {
    /**
     * Create a ContainerRegistryClient for a registry. If no args are supplied the client is constructed for Docker
     * Hub with no authentication.
     */
    fun create(
        url: URL = URL(DOCKER_HUB_URL),
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): T
}

interface ContainerImageClientFactory<T> {
    fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): T
}

interface SuspendingContainerImageClientFactory<T> {
    suspend fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): T
}
