package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.BlockingContainerImageClientFactory
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.common.runBlocking
import kotlinx.coroutines.delay
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.net.URI

fun main() {
    val url = "http://localhost:5000".let(::URI)
    val credentials = null
    val client = BlockingContainerImageClientFactory.create(url = url, credentials = credentials)

    shouldNotThrowAny {
        client.testConnection()
    }

    val repository = Repository("hello-world")
    val tag = Tag("latest")

    // client.manifestDelete(repository, tag)

    val data = SystemFileSystem.source("/home/mirko/Downloads/hello-world.tar".let(::Path)).buffered()

    client.upload(repository, tag, data.asInputStream())

    runBlocking {
        delay(1000)
    }

    shouldNotThrowAny {
        val sink = client.download(repository, tag)
    }
}