rootProject.name = "kirc"
include("kirc-blocking")
include("kirc-core")
include("kirc-image")
include("kirc-reactive")
include("kirc-suspending")
include("aot-smoke-test")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("coroutines") {
            library("bom", "org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.1")
        }
        create("jackson") {
            library("bom", "com.fasterxml.jackson:jackson-bom:2.17.1")
        }
        create("graalHints") {
            version("goodforgod", "1.2.0")
            library("annotations", "io.goodforgod", "graalvm-hint-annotations").versionRef("goodforgod")
            library("processor", "io.goodforgod", "graalvm-hint-processor").versionRef("goodforgod")
        }
        create("tests") {
            library("junit-bom", "org.junit:junit-bom:5.10.2")
            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").withoutVersion()
            library("junit-params", "org.junit.jupiter", "junit-jupiter-params").withoutVersion()
            library("junit-engine", "org.junit.jupiter", "junit-jupiter-engine").withoutVersion()
            bundle("junit", listOf("junit-api", "junit-params", "junit-engine"))

            version("kotest", "5.9.1")
            library("kotest-runner", "io.kotest", "kotest-runner-junit5").versionRef("kotest")
            library("kotest-assertions", "io.kotest", "kotest-assertions-core-jvm").versionRef("kotest")
            bundle("kotest", listOf("kotest-runner", "kotest-assertions"))
        }
    }
}
