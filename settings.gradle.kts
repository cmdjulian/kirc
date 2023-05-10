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
            library("bom", "org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.0")
        }
        create("jackson") {
            library("bom", "com.fasterxml.jackson:jackson-bom:2.15.0")
        }
        create("tests") {
            library("junit-bom", "org.junit:junit-bom:5.9.3")
            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").withoutVersion()
            library("junit-params", "org.junit.jupiter", "junit-jupiter-params").withoutVersion()
            library("junit-engine", "org.junit.jupiter", "junit-jupiter-engine").withoutVersion()
            bundle("junit", listOf("junit-api", "junit-params", "junit-engine"))

            version("kotest", "5.6.2")
            library("kotest-runner", "io.kotest", "kotest-runner-junit5").versionRef("kotest")
            library("kotest-assertions", "io.kotest", "kotest-assertions-core-jvm").versionRef("kotest")
            bundle("kotest", listOf("kotest-runner", "kotest-assertions"))
        }
    }
}