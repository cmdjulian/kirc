plugins {
    `java-library`
    kotlin("libs.publisher")
    kotlin("kapt")
}

dependencies {
    implementation(project(":kirc-image"))

    // Kotlin
    implementation(platform(coroutines.bom))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // graal reflect config
    kapt(graalHints.processor)
    compileOnly(graalHints.annotations)

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
