import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.util.*

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm") version "1.8.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "11.2.0" apply false
    // Gradle task "dependencyCheckAnalyze" to check for security CVEs in dependencies
    id("org.owasp.dependencycheck") version "8.1.0" apply false
    // check for dependency updates via task "dependencyUpdates --refresh-dependencies"
    id("com.github.ben-manes.versions") version "0.46.0" apply false
}

allprojects {
    group = "de.cmdjulian"
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

subprojects {
    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.gradle.maven-publish")
        plugin("org.jlleitschuh.gradle.ktlint")
        plugin("org.owasp.dependencycheck")
        plugin("com.github.ben-manes.versions")
    }

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(11)
    }

    configure<KtlintExtension> {
        version.set("0.48.2")
        enableExperimentalRules.set(true)
    }

    tasks.withType<DependencyUpdatesTask>().configureEach {
        fun isNonStable(version: String): Boolean {
            val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { s -> s in version.uppercase(Locale.getDefault()) }
            val isStable = stableKeyword || Regex("^[0-9,.v-]+(-r)?$").matches(version)

            return !isStable
        }

        checkForGradleUpdate = true
        outputFormatter = "json"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
        gradleReleaseChannel = "current"

        rejectVersionIf {
            isNonStable(candidate.version)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            javaParameters = true
            freeCompilerArgs = listOf("-Xjsr305=strict", "-Xemit-jvm-type-annotations", "-Xcontext-receivers", "-Xjvm-default=all")
            jvmTarget = "${JavaVersion.VERSION_11}"
        }
    }
}