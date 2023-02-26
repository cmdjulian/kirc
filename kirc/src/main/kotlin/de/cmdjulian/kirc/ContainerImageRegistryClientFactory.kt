package de.cmdjulian.kirc

import com.github.kittinunf.fuel.core.FuelManager
import de.cmdjulian.kirc.impl.AsyncContainerImageClientImpl
import de.cmdjulian.kirc.impl.AsyncContainerImageRegistryClientImpl
import de.cmdjulian.kirc.impl.ContainerRegistryApi
import de.cmdjulian.kirc.impl.ContainerRegistryApiImpl
import de.cmdjulian.kirc.model.ContainerImageName
import de.cmdjulian.kirc.utils.InsecureSSLSocketFactory
import de.cmdjulian.kirc.utils.NoopHostnameVerifier
import kotlinx.coroutines.runBlocking
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

const val DOCKER_HUB_URL = "https://registry.hub.docker.com"

class RegistryCredentials(val username: String, val password: String)

object ContainerImageRegistryClientFactory {
    /**
     * Create a ContainerRegistryClient for a registry. If no args are supplied the client is constructed for Docker
     * Hub with no authentication.
     */
    @JvmStatic
    @JvmOverloads
    fun create(
        url: URL = URL(DOCKER_HUB_URL),
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): AsyncContainerImageRegistryClient {
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

        return AsyncContainerImageRegistryClientImpl(api)
    }

    @JvmSynthetic
    suspend fun createKt(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ): AsyncContainerImageClient {
        val url = "${if (insecure) "http://" else "https://"}${image.registry}"
        val client = create(URL(url), credentials, proxy, skipTlsVerify, keystore)

        return AsyncContainerImageClientImpl(client, image)
    }

    @JvmStatic
    @JvmOverloads
    fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
    ) = runBlocking { createKt(image, credentials, proxy, insecure, skipTlsVerify, keystore) }
}
