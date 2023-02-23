package de.cmdjulian.distribution

import com.github.kittinunf.fuel.core.FuelManager
import de.cmdjulian.distribution.config.ProxyConfig
import de.cmdjulian.distribution.config.RegistryCredentials
import de.cmdjulian.distribution.impl.ContainerRegistryApi
import de.cmdjulian.distribution.impl.ContainerRegistryApiImpl
import de.cmdjulian.distribution.impl.CoroutineContainerRegistryClientImpl
import de.cmdjulian.distribution.impl.CoroutineImageClientImpl
import de.cmdjulian.distribution.model.DockerImageSlug
import java.net.URL

const val DOCKER_HUB_URL = "https://registry.hub.docker.com"

@Suppress("unused", "MemberVisibilityCanBePrivate", "HttpUrlsUsage")
object ContainerRegistryClientFactory {

    data class ImageClientConfig(
        val image: DockerImageSlug,
        val credentials: RegistryCredentials? = null,
        val config: ProxyConfig? = null,
        val insecure: Boolean = false,
    )

    /**
     * Create a DistributionClient for a registry. If no args are supplied the client is constructed for Docker Hub with
     * no auth.
     */
    fun create(
        url: URL = URL(DOCKER_HUB_URL),
        credentials: RegistryCredentials? = null,
        config: ProxyConfig? = null,
    ): CoroutineContainerRegistryClient {
        val fuel = FuelManager().apply {
            basePath = if (this.toString() == "https://docker.io") DOCKER_HUB_URL else url.toString()
            proxy = config?.proxy
        }
        val api: ContainerRegistryApi = ContainerRegistryApiImpl(fuel, credentials)

        return CoroutineContainerRegistryClientImpl(api)
    }

    fun create(image: DockerImageSlug): CoroutineImageClient = create(ImageClientConfig(image))

    fun create(config: ImageClientConfig): CoroutineImageClient {
        val client = create(
            URL((if (config.insecure) "http://" else "https://") + config.image.registry.toString()),
            config.credentials,
            config.config,
        )

        return CoroutineImageClientImpl(client as CoroutineContainerRegistryClientImpl, config.image)
    }
}
