plugins {
    `java-library`
    kotlin("libs.publisher")
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
    }
}

dependencies {
    // jackson
    implementation(platform(jackson.bom))
    implementation("com.fasterxml.jackson.core:jackson-databind")
}

kotlinPublications {
    publication {
        publicationName = "image"
        description = "kirc module for parsing container image name and components"
    }
}
