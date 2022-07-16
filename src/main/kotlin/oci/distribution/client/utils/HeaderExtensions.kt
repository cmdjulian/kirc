package oci.distribution.client.utils

import okhttp3.Headers
import java.util.TreeMap

fun Headers.getIgnoreCase(header: String): String? {
    return TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply { putAll(this) }[header]
}
