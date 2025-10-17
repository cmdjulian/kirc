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
    implementation("io.ktor:ktor-client-auth:2.3.12")

    // graal reflect config
    kapt(graalHints.processor)
    compileOnly(graalHints.annotations)

    // http client
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-logging:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-jackson:2.3.12")
    // explicit result library
    implementation("com.github.kittinunf.result:result:5.0.0")

    // auth header parsing
    implementation("im.toss:http-auth-parser:0.1.2")

    // jackson
    implementation(platform(jackson.bom))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")

    // insecure connections
    implementation("io.github.hakky54:sslcontext-kickstart:8.3.7")

    // tar file handling
    implementation("org.apache.commons:commons-compress:1.26.1")

    // tests
    testImplementation(project(":kirc-blocking"))

    testImplementation(platform(tests.junit.bom))
    testImplementation(tests.bundles.junit)
    testImplementation(tests.bundles.kotest)

    // logback logger for tests
    testImplementation("ch.qos.logback:logback-classic:1.5.18")

    // resource injection
    testImplementation("io.hosuaby:inject-resources-junit-jupiter:0.3.3")

    // test container
    testImplementation("org.testcontainers:testcontainers:1.21.3")
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
