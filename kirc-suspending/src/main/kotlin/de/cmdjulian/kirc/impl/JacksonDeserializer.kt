package de.cmdjulian.kirc.impl

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.ResponseDeserializable

internal inline fun <reified T : Any> jacksonDeserializer() = object : ResponseDeserializable<T> {
    override fun deserialize(content: String): T = JsonMapper.readValue(content)
}
