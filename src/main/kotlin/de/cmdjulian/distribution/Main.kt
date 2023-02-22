package de.cmdjulian.distribution

import de.cmdjulian.distribution.model.Repository
import de.cmdjulian.distribution.model.Tag

fun main(vararg args: String) {
    val client = ContainerRegistryClientFactory.create().toBlockingClient()

    val digest = client.manifestDigest(Repository("cmdjulian/mopy"), Tag("latest")).also(::println)
    val manifest = client.manifest(Repository("cmdjulian/mopy"), Tag("ttt")).also(::println)
}
