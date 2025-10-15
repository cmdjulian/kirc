package de.cmdjulian.kirc.impl.serialization

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature.NullToEmptyCollection
import com.fasterxml.jackson.module.kotlin.KotlinFeature.NullToEmptyMap
import com.fasterxml.jackson.module.kotlin.KotlinFeature.SingletonSupport
import com.fasterxml.jackson.module.kotlin.KotlinFeature.StrictNullChecks
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle

private val KotlinModule = kotlinModule {
    configure(NullToEmptyCollection, true)
    configure(NullToEmptyMap, true)
    configure(SingletonSupport, true)
    configure(StrictNullChecks, true)
}

internal val JsonMapper = jsonMapper {
    addModules(KotlinModule, JavaTimeModule(), Jdk8Module())

    configure(READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
    configure(FAIL_ON_NULL_FOR_PRIMITIVES, true)
    configure(FAIL_ON_UNKNOWN_PROPERTIES, false)

    addMixIn(Manifest::class.java, ManifestMixIn::class.java)
    addMixIn(ManifestSingle::class.java, ManifestSingleMixIn::class.java)
    addMixIn(ManifestList::class.java, ManifestListMixIn::class.java)
}
