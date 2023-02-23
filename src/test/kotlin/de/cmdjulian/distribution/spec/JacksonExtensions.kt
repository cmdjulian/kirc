package de.cmdjulian.distribution.spec

import de.cmdjulian.distribution.impl.JsonMapper

internal inline fun <reified T : Any> String.unmarshal(): T {
    return JsonMapper.readValue(this, T::class.java)
}
