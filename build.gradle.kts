import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.publisher.apache2
import org.jetbrains.kotlinx.publisher.developer
import org.jetbrains.kotlinx.publisher.githubRepo
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.util.*

plugins {
    kotlin("jvm") version "1.8.22" apply false
    kotlin("libs.publisher") version "0.0.61-dev-34"
    id("org.jlleitschuh.gradle.ktlint") version "11.4.2" apply false
    id("org.owasp.dependencycheck") version "8.3.1" apply false // "dependencyCheckAnalyze"
    id("com.github.ben-manes.versions") version "0.47.0" apply false // "dependencyUpdates --refresh-dependencies"
    id("me.qoomon.git-versioning") version "6.4.2"
}

kotlinPublications {
    defaultGroup.set("com.github.cmdjulian.kirc")
    fairDokkaJars.set(false)

    pom {
        inceptionYear.set("2022")
        licenses {
            apache2()
        }
        githubRepo("cmdjulian", "kirc")
        developers {
            developer("cmdjulian", "Julian Goede", "julian.goede@pm.me")
        }
    }

    localRepositories {
        defaultLocalMavenRepository()
    }
}

version = "0.0.0-SNAPSHOT"
gitVersioning.apply {
    refs {
        branch(".+") { version = "\${ref}-SNAPSHOT-\${commit.short}" }
        tag("v(?<version>.*)") { version = "\${ref.version}" }
    }
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
        plugin("org.jlleitschuh.gradle.ktlint")
        plugin("org.owasp.dependencycheck")
        plugin("com.github.ben-manes.versions")
    }

    // only configured if subProject applies the publishing plugin
    plugins.withId("org.jetbrains.kotlin.libs.publisher") {
        publishing {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/cmdjulian/kirc")
                    credentials {
                        username = project.findProperty("gpr.user") as? String? ?: System.getenv("USERNAME")
                        password = project.findProperty("gpr.key") as? String? ?: System.getenv("TOKEN")
                    }
                }
            }
        }
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
