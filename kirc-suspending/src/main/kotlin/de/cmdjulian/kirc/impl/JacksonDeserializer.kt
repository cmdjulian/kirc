package de.cmdjulian.kirc.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream

internal class JacksonDeserializer<T : Any>(private val clazz: Class<T>) {
    private val logger = KotlinLogging.logger { }

    fun deserialize(content: String): T = try {
        JsonMapper.readValue(content, clazz)
    } catch (e: Exception) {
        logger.error(e) { "unexpected error on json deserialization" }
        throw e
    }

    fun deserialize(bytes: ByteArray): T = try {
        JsonMapper.readValue(bytes, clazz)
    } catch (e: Exception) {
        logger.error(e) { "unexpected error on json deserialization" }
        throw e
    }

    fun deserialize(inputStream: InputStream): T = try {
        JsonMapper.readValue<T>(inputStream, clazz)
    } catch (e: Exception) {
        logger.error(e) { "unexpected error on json stream deserialization" }
        throw e
    }
}

internal inline fun <reified T : Any> jacksonDeserializer() = JacksonDeserializer(T::class.java)
