package de.cmdjulian.kirc.impl

import com.github.kittinunf.fuel.core.ResponseDeserializable
import mu.KotlinLogging

class JacksonDeserializer<T : Any>(private val clazz: Class<T>) : ResponseDeserializable<T> {

    private val logger = KotlinLogging.logger { }

    override fun deserialize(content: String): T = try {
        JsonMapper.readValue(content, clazz)
    } catch (e: Exception) {
        logger.error(e) { "unexpected error on json deserialization" }
        throw e
    }

    override fun deserialize(bytes: ByteArray): T = try {
        JsonMapper.readValue(bytes, clazz)
    } catch (e: Exception) {
        logger.error(e) { "unexpected error on json deserialization" }
        throw e
    }
}

internal inline fun <reified T : Any> jacksonDeserializer() = JacksonDeserializer(T::class.java)
