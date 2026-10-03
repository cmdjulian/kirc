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
    implementation(libs.coroutines)
    api(libs.kotlinx.io)

    // graal reflect config
    kapt(graalHints.processor)
    compileOnly(graalHints.annotations)

    // http client
    implementation("io.ktor:ktor-client-auth:3.6.0")
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-jackson:3.6.0")

    implementation("com.github.ben-manes.caffeine:caffeine:3.3.0")

    // explicit result library
    implementation("com.github.kittinunf.result:result:5.6.0")

    // auth header parsing
    implementation("im.toss:http-auth-parser:0.1.2")

    // jackson
    implementation(platform(jackson.bom))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // logging
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")

    // insecure connections
    implementation("io.github.hakky54:ayza:10.1.0")

    // tar file handling
    implementation("org.apache.commons:commons-compress:1.28.0")

    // tests
    testImplementation(project(":kirc-blocking"))

    testImplementation(platform(tests.junit.bom))
    testImplementation(tests.bundles.junit)
    testImplementation(tests.bundles.kotest)

    // logback logger for tests
    testImplementation("ch.qos.logback:logback-classic:1.6.5")

    // resource injection
    testImplementation("io.hosuaby:inject-resources-junit-jupiter:1.0.0")

    // test container
    testImplementation("org.testcontainers:testcontainers:2.0.5")

    // coroutine testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
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
