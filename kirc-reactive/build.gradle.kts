plugins {
    `java-library`
    kotlin("libs.publisher")
}

group = "com.github.cmdjulian.kirc"

dependencies {
    api(project(":kirc-core"))
    api(project(":kirc-image"))
    api(project(":kirc-tar"))
    implementation(project(":kirc-suspending"))

    api(libs.kotlinx.io)
    api(libs.coroutines.reactor)
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
    }
}

kotlinPublications {
    publication {
        publicationName = "reactive"
        description = "GraalVM compatible project reactor based container image registry client written in kotlin"
    }
}
