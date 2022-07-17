package de.cmdjulian.distribution.utils

import okhttp3.Headers
import java.util.TreeMap

internal fun Headers.getIgnoreCase(header: String): String? {
    return TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply { putAll(this@getIgnoreCase) }[header]
}
