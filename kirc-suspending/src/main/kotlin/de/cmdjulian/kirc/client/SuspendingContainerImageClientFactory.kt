package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.impl.ContainerRegistryApiImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageClientImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageRegistryClientImpl
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.utils.configureAuth
import de.cmdjulian.kirc.utils.configureHttps
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import java.security.KeyStore
import java.time.Duration
import kotlin.io.path.Path

private val kLogger = KotlinLogging.logger {}

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
        timeout: Duration = Duration.ofSeconds(15),
        tmpPath: Path = Path(System.getProperty("java.io.tmpdir")),
    ): SuspendingContainerImageRegistryClient {
        require(keystore == null || !skipTlsVerify) { "can not skip tls verify if a keystore is set" }
        require(timeout.toMillis() in Int.MIN_VALUE..Int.MAX_VALUE) { "timeout in ms has to be a valid int" }

        val client = HttpClient(CIO) {
            engine {
                requestTimeout = timeout.toMillis()
                if (proxy != null) {
                    val address = proxy.address()
                    if (address is InetSocketAddress) {
                        this.proxy = ProxyBuilder.http("http://${address.hostString}:${address.port}")
                    }
                }
                configureHttps(skipTlsVerify, keystore)
            }
            install(ContentNegotiation) {
                // re-use existing ObjectMapper instance
                register(ContentType.Application.Json, JacksonConverter(JsonMapper))
            }
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) = kLogger.info { "Kirc API $message" }
                }
            }
            defaultRequest {
                url(url.toString())
            }
            install(Auth) {
                configureAuth(credentials)
            }
        }

        val api = ContainerRegistryApiImpl(client)
        return SuspendingContainerImageRegistryClientImpl(api, tmpPath)
    }

    @JvmStatic
    suspend fun create(
        image: ContainerImageName,
        credentials: RegistryCredentials? = null,
        proxy: Proxy? = null,
        insecure: Boolean = false,
        skipTlsVerify: Boolean = false,
        keystore: KeyStore? = null,
        timeout: Duration = Duration.ofSeconds(15),
        tmpPath: Path = Path(System.getProperty("java.io.tmpdir")),
    ): SuspendingContainerImageClient {
        val url = "${if (insecure) "http://" else "https://"}${image.registry}"
        val client = create(URI(url), credentials, proxy, skipTlsVerify, keystore, timeout, tmpPath)

        return SuspendingContainerImageClientImpl(client, image)
    }
}
