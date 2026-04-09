package de.cmdjulian.kirc

import de.cmdjulian.kirc.impl.serialization.JsonMapper

internal inline fun <reified T : Any> String.unmarshal(): T = JsonMapper.readValue(this, T::class.java)
