package de.cmdjulian.distribution

import com.github.kittinunf.fuel.core.FuelManager
import de.cmdjulian.distribution.impl.ContainerRegistryApi
import de.cmdjulian.distribution.impl.ContainerRegistryApiImpl
import de.cmdjulian.distribution.impl.CoroutineContainerRegistryClientImpl
import de.cmdjulian.distribution.impl.CoroutineImageClientImpl
import de.cmdjulian.distribution.model.ContainerImageName
import de.cmdjulian.distribution.utils.InsecureSSLSocketFactory
import de.cmdjulian.distribution.utils.NoopHostnameVerifier
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

const val DOCKER_HUB_URL = "https://registry.hub.docker.com"

class RegistryCredentials(val username: String, val password: String)

object ContainerRegistryClientFactory {
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
    ): CoroutineContainerRegistryClient {
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

        return CoroutineContainerRegistryClientImpl(api)
    }

    fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): CoroutineImageClient {
        val url = "${if (insecure) "http://" else "https://"}${image.registry}"
        val client = create(URL(url), credentials, proxy, skipTlsVerify, keystore)

        return CoroutineImageClientImpl(client, image)
    }
}
