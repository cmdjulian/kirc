package de.cmdjulian.kirc

import de.cmdjulian.kirc.impl.JsonMapper

internal inline fun <reified T : Any> String.unmarshal(): T {
    return JsonMapper.readValue(this, T::class.java)
}
