plugins {
    `java-library`
    kotlin("libs.publisher")
    kotlin("kapt")
}

dependencies {
    implementation(project(":kirc-image"))

    // reflection registration
    kapt("io.goodforgod:graalvm-hint-processor:1.2.0")
    compileOnly("io.goodforgod:graalvm-hint-annotations:1.2.0")

    // jackson
    implementation(platform(jackson.bom))
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // tests
    testImplementation(platform(tests.junit.bom))
    testImplementation(tests.bundles.junit)
    testImplementation(tests.bundles.kotest)
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
    }
}

kotlinPublications {
    publication {
        publicationName = "core"
        description = "kirc core components"
    }
}
