package de.cmdjulian.kirc.client

import com.github.kittinunf.fuel.core.FuelManager
import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.impl.ContainerRegistryApiImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageClientImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageRegistryClientImpl
import de.cmdjulian.kirc.utils.InsecureSSLSocketFactory
import de.cmdjulian.kirc.utils.NoopHostnameVerifier
import java.net.Proxy
import java.net.URI
import java.security.KeyStore
import java.time.Duration

object SuspendingContainerImageClientFactory {

    const val DOCKER_HUB_REGISTRY_URL = "https://registry.hub.docker.com/"

    /**
     * Create a ContainerRegistryClient for a registry. If no args are supplied the client is constructed for Docker
     * Hub with no authentication.
     */
    @JvmStatic
    fun create(
        url: URI = URI(DOCKER_HUB_REGISTRY_URL),
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
        timeout: Duration = Duration.ofSeconds(5),
    ): SuspendingContainerImageRegistryClient {
        require(keystore == null || !skipTlsVerify) { "can not skip tls verify if a keystore is set" }
        require(timeout.toMillis() in Int.MIN_VALUE..Int.MAX_VALUE) { "timeout in ms has to be a valid int" }

        val fuel = FuelManager().apply {
            this.basePath = url.toString()
            this.proxy = proxy
            this.keystore = keystore
            this.timeoutInMillisecond = timeout.toMillis().toInt()
            this.timeoutReadInMillisecond = timeout.toMillis().toInt()

            if (skipTlsVerify) {
                hostnameVerifier = NoopHostnameVerifier
                socketFactory = InsecureSSLSocketFactory
            }
        }

        return SuspendingContainerImageRegistryClientImpl(ContainerRegistryApiImpl(fuel, credentials))
    }

    @JvmStatic
    suspend fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
        timeout: Duration = Duration.ofSeconds(5),
    ): SuspendingContainerImageClient {
        val url = "${if (insecure) "http://" else "https://"}${image.registry}"
        val client = create(URI(url), credentials, proxy, skipTlsVerify, keystore, timeout)

        return SuspendingContainerImageClientImpl(client, image)
    }
}
