package de.cmdjulian.kirc.client.blocking

import de.cmdjulian.kirc.client.ContainerImageClientFactory
import de.cmdjulian.kirc.client.ContainerImageRegistryClientFactory
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.client.suspending.SuspendingClientFactory
import de.cmdjulian.kirc.model.ContainerImageName
import kotlinx.coroutines.runBlocking
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

object BlockingClientFactory :
    ContainerImageRegistryClientFactory<BlockingContainerImageRegistryClient>,
    ContainerImageClientFactory<BlockingContainerImageClient> {

    override fun create(
        url: URL,
        credentials: RegistryCredentials?,
        proxy: Proxy?,
        skipTlsVerify: Boolean,
        keystore: KeyStore?,
    ): BlockingContainerImageRegistryClient =
        SuspendingClientFactory.create(url, credentials, proxy, skipTlsVerify, keystore).toBlockingClient()

    override fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials?,
        proxy: Proxy?,
        insecure: Boolean,
        skipTlsVerify: Boolean,
        keystore: KeyStore?,
    ): BlockingContainerImageClient = runBlocking {
        SuspendingClientFactory.create(image, credentials, proxy, insecure, skipTlsVerify, keystore).toBlockingClient()
    }
}
