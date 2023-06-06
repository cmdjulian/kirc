package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.ContainerImageName
import kotlinx.coroutines.runBlocking
import java.net.Proxy
import java.net.URI
import java.security.KeyStore

object BlockingContainerImageClientFactory {

    @Suppress("MemberVisibilityCanBePrivate")
    const val DOCKER_HUB_REGISTRY_URL = SuspendingContainerImageClientFactory.DOCKER_HUB_REGISTRY_URL

    /**
     * Create a ContainerRegistryClient for a registry. If no args are supplied the client is constructed for Docker
     * Hub with no authentication.
     */
    @JvmStatic
    @JvmOverloads
    fun create(
        url: URI = URI(DOCKER_HUB_REGISTRY_URL),
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): BlockingContainerImageRegistryClient =
        SuspendingContainerImageClientFactory.create(url, credentials, proxy, skipTlsVerify, keystore)
            .toBlockingClient()

    @JvmStatic
    @JvmOverloads
    fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): BlockingContainerImageClient = runBlocking {
        SuspendingContainerImageClientFactory.create(image, credentials, proxy, insecure, skipTlsVerify, keystore)
            .toBlockingClient()
    }
}
