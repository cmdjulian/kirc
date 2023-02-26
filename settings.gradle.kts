rootProject.name = "kirc"
include("kirc-blocking")
include("kirc-reactive")
include("kirc-suspending")

pluginManagement {
    plugins {
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}