package de.cmdjulian.kirc.client

import com.github.kittinunf.fuel.core.FuelManager
import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.impl.ContainerRegistryApi
import de.cmdjulian.kirc.impl.ContainerRegistryApiImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageClientImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageRegistryClientImpl
import de.cmdjulian.kirc.utils.InsecureSSLSocketFactory
import de.cmdjulian.kirc.utils.NoopHostnameVerifier
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

const val DOCKER_HUB_REGISTRY_URL = "https://registry.hub.docker.com"

object SuspendingClientFactory {
    /**
     * Create a ContainerRegistryClient for a registry. If no args are supplied the client is constructed for Docker
     * Hub with no authentication.
     */
    @JvmStatic
    fun create(
        url: URL = URL(DOCKER_HUB_REGISTRY_URL),
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): SuspendingContainerImageRegistryClient {
        require(keystore == null || !skipTlsVerify) { "can not skip tls verify if a keystore is set" }

        val fuel = FuelManager().apply {
            this.basePath = url.toString()
            this.proxy = proxy

            if (skipTlsVerify) {
                hostnameVerifier = NoopHostnameVerifier
                socketFactory = InsecureSSLSocketFactory
            }
        }
        val api: ContainerRegistryApi = ContainerRegistryApiImpl(fuel, credentials)

        return SuspendingContainerImageRegistryClientImpl(api)
    }

    @JvmStatic
    suspend fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): SuspendingContainerImageClient {
        val url = "${if (insecure) "http://" else "https://"}${image.registry}"
        val client = create(URL(url), credentials, proxy, skipTlsVerify, keystore)

        return SuspendingContainerImageClientImpl(client, image)
    }
}
