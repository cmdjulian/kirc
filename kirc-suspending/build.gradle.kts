plugins {
    `java-library`
    kotlin("libs.publisher")
}

dependencies {
    api(project(":kirc-core"))
    api(project(":kirc-image"))

    // kotlin
    implementation(kotlin("stdlib"))
    implementation(platform(coroutines.bom))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

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
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // insecure connections
    implementation("io.github.hakky54:sslcontext-kickstart:8.1.6")

    // tests
    testImplementation(platform(tests.junit.bom))
    testImplementation(tests.bundles.junit)
    testImplementation(tests.bundles.kotest)

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
        publicationName.set("suspending")
        description.set("GraalVM compatible coroutine based container image registry client written in kotlin")
    }
}
