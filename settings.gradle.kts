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
        create("libs") {
            version("coroutines", "1.10.2")
            library("kotlinx-io", "org.jetbrains.kotlinx", "kotlinx-io-core").version("0.8.0")
            library("coroutines", "org.jetbrains.kotlinx", "kotlinx-coroutines-core").versionRef("coroutines")
            library("coroutines-reactor", "org.jetbrains.kotlinx", "kotlinx-coroutines-reactor").versionRef("coroutines")
        }
        create("jackson") {
            library("bom", "com.fasterxml.jackson:jackson-bom:2.20.0")
        }
        create("graalHints") {
            version("goodforgod", "1.2.0")
            library("annotations", "io.goodforgod", "graalvm-hint-annotations").versionRef("goodforgod")
            library("processor", "io.goodforgod", "graalvm-hint-processor").versionRef("goodforgod")
        }
        create("tests") {
            library("junit-bom", "org.junit:junit-bom:5.14.0")
            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").withoutVersion()
            library("junit-params", "org.junit.jupiter", "junit-jupiter-params").withoutVersion()
            library("junit-engine", "org.junit.jupiter", "junit-jupiter-engine").withoutVersion()
            bundle("junit", listOf("junit-api", "junit-params", "junit-engine"))

            version("kotest", "6.0.4")
            library("kotest-runner", "io.kotest", "kotest-runner-junit5").versionRef("kotest")
            library("kotest-assertions", "io.kotest", "kotest-assertions-core-jvm").versionRef("kotest")
            bundle("kotest", listOf("kotest-runner", "kotest-assertions"))
        }
    }
}
