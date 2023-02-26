package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.ContainerImageName
import kotlinx.coroutines.runBlocking
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

object BlockingClientFactory {
    /**
     * Create a ContainerRegistryClient for a registry. If no args are supplied the client is constructed for Docker
     * Hub with no authentication.
     */
    @JvmStatic
    @JvmOverloads
    fun create(
        url: URL = URL(ContainerImageName.DOCKER_HUB_REGISTRY),
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): BlockingContainerImageRegistryClient =
        SuspendingClientFactory.create(url, credentials, proxy, skipTlsVerify, keystore).toBlockingClient()

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
        SuspendingClientFactory.create(image, credentials, proxy, insecure, skipTlsVerify, keystore).toBlockingClient()
    }
}
