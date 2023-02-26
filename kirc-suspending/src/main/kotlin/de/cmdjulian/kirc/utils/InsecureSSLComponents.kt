package de.cmdjulian.kirc.utils

import nl.altindag.ssl.SSLFactory
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory

internal object NoopHostnameVerifier : HostnameVerifier {
    override fun verify(s: String?, sslSession: SSLSession?): Boolean = true
}

internal object InsecureSSLSocketFactory : SSLSocketFactory() {
    private val delegate = SSLFactory.builder()
        .withUnsafeTrustMaterial()
        .withUnsafeHostnameVerifier()
        .build()
        .sslSocketFactory

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        delegate.createSocket(s, host, port, autoClose)

    override fun createSocket(host: String, port: Int): Socket = delegate.createSocket(host, port)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        delegate.createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket = delegate.createSocket(host, port)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        delegate.createSocket(address, port, localAddress, localPort)

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket, consumed: InputStream, autoClose: Boolean): Socket =
        delegate.createSocket(s, consumed, autoClose)
}
