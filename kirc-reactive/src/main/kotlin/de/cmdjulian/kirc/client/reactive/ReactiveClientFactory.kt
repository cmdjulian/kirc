package de.cmdjulian.kirc.client.reactive

import de.cmdjulian.kirc.client.ContainerImageClientFactory
import de.cmdjulian.kirc.client.ContainerImageRegistryClientFactory
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.client.suspending.SuspendingClientFactory
import de.cmdjulian.kirc.model.ContainerImageName
import kotlinx.coroutines.runBlocking
import java.net.Proxy
import java.net.URL
import java.security.KeyStore

object ReactiveClientFactory :
    ContainerImageRegistryClientFactory<ReactiveContainerImageRegistryClient>,
    ContainerImageClientFactory<ReactiveContainerImageClient> {

    override fun create(
        url: URL,
        credentials: RegistryCredentials?,
        proxy: Proxy?,
        skipTlsVerify: Boolean,
        keystore: KeyStore?,
    ): ReactiveContainerImageRegistryClient =
        SuspendingClientFactory.create(url, credentials, proxy, skipTlsVerify, keystore).toReactiveClient()

    override fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials?,
        proxy: Proxy?,
        insecure: Boolean,
        skipTlsVerify: Boolean,
        keystore: KeyStore?,
    ): ReactiveContainerImageClient = runBlocking {
        SuspendingClientFactory.create(image, credentials, proxy, insecure, skipTlsVerify, keystore).toReactiveClient()
    }
}
