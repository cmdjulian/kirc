package de.cmdjulian.distribution.impl

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import okhttp3.OkHttpClient
import java.net.URL

internal val DOCKER_HUB_URL = URL("https://registry-1.docker.io")

internal val httpClient = OkHttpClient.Builder()
    .apply {
        interceptors().add { chain ->
            chain.request().newBuilder().header("Accept", "application/json").build().let(chain::proceed)
        }
    }
    .build()

internal val jsonMapper = jsonMapper {
    addModules(kotlinModule())
    addModules(JavaTimeModule())
    addModules(Jdk8Module())
}