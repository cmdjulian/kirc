package de.cmdjulian.kirc.impl

import io.ktor.utils.io.ByteReadChannel

sealed interface RequestBodyType {
    object Binary : RequestBodyType

    fun interface Stream : RequestBodyType {
        suspend fun channel(): ByteReadChannel
    }
}