package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.ContainerImageName
import kotlinx.coroutines.runBlocking
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

object ReactiveClientFactory {
    /**
     * Create a ContainerRegistryClient for a registry. If no args are supplied the client is constructed for Docker
     * Hub with no authentication.
     */
    @JvmStatic
    @JvmOverloads
    fun create(
        url: URL = URL(DOCKER_HUB_REGISTRY_URL),
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): ReactiveContainerImageRegistryClient =
        SuspendingClientFactory.create(url, credentials, proxy, skipTlsVerify, keystore).toReactiveClient()

    @JvmStatic
    @JvmOverloads
    fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): ReactiveContainerImageClient = runBlocking {
        SuspendingClientFactory.create(image, credentials, proxy, insecure, skipTlsVerify, keystore).toReactiveClient()
    }
}
