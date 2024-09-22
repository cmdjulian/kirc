plugins {
    `java-library`
    kotlin("libs.publisher")
    kotlin("kapt")
}

dependencies {
    api(project(":kirc-core"))
    api(project(":kirc-image"))

    // kotlin
    implementation(kotlin("stdlib"))
    implementation(platform(coroutines.bom))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // graal reflect config
    kapt(graalHints.processor)
    compileOnly(graalHints.annotations)

    // fuel
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")

    // auth header parsing
    implementation("im.toss:http-auth-parser:0.1.2")

    // jackson
    implementation(platform(jackson.bom))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.9")

    // insecure connections
    implementation("io.github.hakky54:sslcontext-kickstart:8.3.7")

    // tests
    testImplementation(platform(tests.junit.bom))
    testImplementation(tests.bundles.junit)
    testImplementation(tests.bundles.kotest)

    // logback logger for tests
    testImplementation("ch.qos.logback:logback-classic:1.5.6")

    // resource injection
    testImplementation("io.hosuaby:inject-resources-junit-jupiter:0.3.3")
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
    }
}

kotlinPublications {
    publication {
        publicationName = "suspending"
        description = "GraalVM compatible coroutine based container image registry client written in kotlin"
    }
}
