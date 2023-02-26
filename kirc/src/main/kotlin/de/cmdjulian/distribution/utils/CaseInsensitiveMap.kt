package de.cmdjulian.distribution.utils

import java.util.*

internal class CaseInsensitiveMap<V>(map: Map<String, V>) : HashMap<String, V>() {
    init {
        map.forEach { (key, value) -> put(key, value) }
    }

    override fun get(key: String): V? {
        return super.get(key.lowercase(Locale.getDefault()))
    }

    override fun put(key: String, value: V): V? {
        return super.put(key.lowercase(Locale.getDefault()), value)
    }

    override fun containsKey(key: String): Boolean {
        return super.containsKey(key.lowercase(Locale.getDefault()))
    }
}
