plugins {
    `java-library`
    kotlin("libs.publisher")
}

group = "com.github.cmdjulian.kirc"

dependencies {
    api(project(":kirc-core"))

    // kotlin
    implementation(kotlin("stdlib"))
    api(libs.coroutines)
    api(libs.kotlinx.io)

    // tar file handling
    api("org.apache.commons:commons-compress:1.28.0")

    // jackson (needed for InvalidDefinitionException and JsonMapper type)
    implementation(platform(jackson.bom))
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
        publicationName = "tar"
        description = "kirc tar image extraction utilities"
    }
}
