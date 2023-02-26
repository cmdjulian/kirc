package de.cmdjulian.kirc.client.suspending

import com.github.kittinunf.fuel.core.FuelManager
import de.cmdjulian.kirc.client.ContainerImageRegistryClientFactory
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.client.SuspendingContainerImageClientFactory
import de.cmdjulian.kirc.impl.ContainerRegistryApi
import de.cmdjulian.kirc.impl.ContainerRegistryApiImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageClientImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageRegistryClientImpl
import de.cmdjulian.kirc.model.ContainerImageName
import de.cmdjulian.kirc.utils.InsecureSSLSocketFactory
import de.cmdjulian.kirc.utils.NoopHostnameVerifier
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

object SuspendingClientFactory :
    ContainerImageRegistryClientFactory<SuspendingContainerImageRegistryClient>,
    SuspendingContainerImageClientFactory<SuspendingContainerImageClient> {

    override fun create(
        url: URL,
        credentials: RegistryCredentials?,
        proxy: Proxy?,
        skipTlsVerify: Boolean,
        keystore: KeyStore?,
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

    override suspend fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials?,
        proxy: Proxy?,
        insecure: Boolean,
        skipTlsVerify: Boolean,
        keystore: KeyStore?,
    ): SuspendingContainerImageClient {
        val url = "${if (insecure) "http://" else "https://"}${image.registry}"
        val client = create(URL(url), credentials, proxy, skipTlsVerify, keystore)

        return SuspendingContainerImageClientImpl(client, image)
    }
}
