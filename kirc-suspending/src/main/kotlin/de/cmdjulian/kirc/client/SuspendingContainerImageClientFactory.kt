package de.cmdjulian.kirc.client

import de.cmdjulian.kirc.image.ContainerImageName
import de.cmdjulian.kirc.impl.ContainerRegistryApiImpl
import de.cmdjulian.kirc.impl.JsonMapper
import de.cmdjulian.kirc.impl.SuspendingContainerImageClientImpl
import de.cmdjulian.kirc.impl.SuspendingContainerImageRegistryClientImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.io.path.Path

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
        tmpPath: Path = Path(System.getProperty("java.io.tmpdir")),
    ): SuspendingContainerImageRegistryClient {
        require(keystore == null || !skipTlsVerify) { "can not skip tls verify if a keystore is set" }
        require(timeout.toMillis() in Int.MIN_VALUE..Int.MAX_VALUE) { "timeout in ms has to be a valid int" }

        val client = HttpClient(CIO) {
            engine {
                requestTimeout = timeout.toMillis()
                if (proxy != null) {
                    val address = proxy.address()
                    if (address is java.net.InetSocketAddress) {
                        this.proxy = ProxyBuilder.http("http://${address.hostString}:${address.port}")
                    }
                }
                if (skipTlsVerify) {
                    https {
                        trustManager = object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}

                            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}

                            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                        }
                    }
                } else if (keystore != null) {
                    https {
                        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                        tmf.init(keystore)
                        trustManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
                    }
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = timeout.toMillis()
                connectTimeoutMillis = timeout.toMillis()
                socketTimeoutMillis = timeout.toMillis()
            }
            install(ContentNegotiation) {
                // re-use existing ObjectMapper instance
                register(ContentType.Application.Json, JacksonConverter(JsonMapper))
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            defaultRequest {
                url(url.toString())
            }
        }

        val api = ContainerRegistryApiImpl(client, credentials, url)
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
        timeout: Duration = Duration.ofSeconds(5),
        tmpPath: Path = Path(System.getProperty("java.io.tmpdir")),
    ): SuspendingContainerImageClient {
        val url = "${if (insecure) "http://" else "https://"}${image.registry}"
        val client = create(URI(url), credentials, proxy, skipTlsVerify, keystore, timeout, tmpPath)

        return SuspendingContainerImageClientImpl(client, image)
    }
}
