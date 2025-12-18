plugins {
    `java-library`
    kotlin("libs.publisher")
}

dependencies {
    api(project(":kirc-core"))
    api(project(":kirc-image"))
    implementation(project(":kirc-suspending"))
    implementation(libs.coroutines)
    api(libs.kotlinx.io)
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
    }
}

kotlinPublications {
    publication {
        publicationName = "blocking"
        description = "GraalVM compatible based container image registry client written in kotlin"
    }
}
