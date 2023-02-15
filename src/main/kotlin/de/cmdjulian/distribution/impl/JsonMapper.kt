package de.cmdjulian.distribution.impl

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature.NullToEmptyCollection
import com.fasterxml.jackson.module.kotlin.KotlinFeature.NullToEmptyMap
import com.fasterxml.jackson.module.kotlin.KotlinFeature.SingletonSupport
import com.fasterxml.jackson.module.kotlin.KotlinFeature.StrictNullChecks
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

internal val JsonMapper = jsonMapper {
    addModules(kotlinModule())
    addModules(JavaTimeModule())
    addModules(Jdk8Module())

    disable(FAIL_ON_UNKNOWN_PROPERTIES)
    kotlinModule {
        withReflectionCacheSize(512)
        configure(NullToEmptyCollection, true)
        configure(NullToEmptyMap, true)
        configure(SingletonSupport, true)
        configure(StrictNullChecks, true)
    }
}
