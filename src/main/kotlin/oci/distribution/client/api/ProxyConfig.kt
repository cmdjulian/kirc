package oci.distribution.client.api

import okhttp3.Authenticator
import java.net.Proxy

class ProxyConfig(val proxy: Proxy, val authenticator: Authenticator? = null)
