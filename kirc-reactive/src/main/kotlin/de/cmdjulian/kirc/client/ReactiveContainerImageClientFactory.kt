package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.ContainerImageName
import kotlinx.coroutines.runBlocking
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import java.security.KeyStore
import java.time.Duration

object ReactiveContainerImageClientFactory {

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
        timeout: Duration = Duration.ofSeconds(15),
        tmpPath: Path,
    ): ReactiveContainerImageRegistryClient =
        SuspendingContainerImageClientFactory.create(url, credentials, proxy, skipTlsVerify, keystore, timeout, tmpPath)
            .toReactiveClient()

    @JvmStatic
    @JvmOverloads
    fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
        timeout: Duration = Duration.ofSeconds(15),
        tmpPath: Path,
    ): ReactiveContainerImageClient = runBlocking {
        SuspendingContainerImageClientFactory
            .create(image, credentials, proxy, insecure, skipTlsVerify, keystore, timeout, tmpPath)
            .toReactiveClient()
    }
}
