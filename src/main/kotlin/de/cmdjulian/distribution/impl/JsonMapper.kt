package de.cmdjulian.distribution.impl

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

internal val JsonMapper = jsonMapper {
    addModules(kotlinModule())
    addModules(JavaTimeModule())
    addModules(Jdk8Module())
}
