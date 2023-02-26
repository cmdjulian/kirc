import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    // publish maven
    `maven-publish`
    // Gradle task "dependencyCheckAnalyze" to check for security CVEs in dependencies
    id("org.owasp.dependencycheck") version "8.1.0"
    // check for dependency updates via task "dependencyUpdates --refresh-dependencies"
    id("com.github.ben-manes.versions") version "0.46.0"
    // linting
    id("org.jlleitschuh.gradle.ktlint") version "11.2.0"
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

group = "de.cmdjulian"
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    // kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    // fuel
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")

    // auth header parsing
    implementation("im.toss:http-auth-parser:0.1.2")

    // jackson
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.14.2"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.github.ProjectMapK:jackson-module-kogera:2.14.2-alpha4")

    // insecure connections
    implementation("io.github.hakky54:sslcontext-kickstart:7.4.9")

    // tests
    testImplementation(platform("org.junit:junit-bom:5.9.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    // resource injection
    testImplementation("io.hosuaby:inject-resources-junit-jupiter:0.3.2")

    val kotest = "5.5.5"
    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotest")
}

tasks {
    val sourcesJar by registering(Jar::class) {
        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    artifacts {
        archives(sourcesJar)
        archives(jar)
    }

    jar {
        archiveBaseName.set("kirc-${project.version}")
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

tasks.withType<DependencyUpdatesTask>().configureEach {
    fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { s -> s in version.toUpperCase() }
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

ktlint {
    version.set("0.48.2")
    enableExperimentalRules.set(true)
}

// calculate dependency size for each dependency with task "depsize"
tasks.register("depsize") {
    group = "reporting"
    description = "Prints dependencies for \"default\" configuration"
    doLast {
        listConfigurationDependencies(configurations["default"].apply { isCanBeResolved = true })
    }
}

tasks.register("depsize-all-configurations") {
    group = "reporting"
    description = "Prints dependencies for all available configurations"
    doLast {
        configurations
            .filter { configuration -> configuration.isCanBeResolved }
            .forEach { configuration -> listConfigurationDependencies(configuration) }
    }
}

fun listConfigurationDependencies(configuration: Configuration) {
    val formatStr = "%,10.2f"

    val summary = buildString {
        append("\nConfiguration name: \"${configuration.name}\"\n")

        when (val size = configuration.sumOf { file -> file.length() / (1024.0 * 1024.0) }) {
            0.0 -> append("No dependencies found")
            else -> {
                append("Total dependencies size:".padEnd(65))
                append("${formatStr.format(size)} Mb\n\n")

                for (it in configuration.sortedBy { file -> -file.length() }) {
                    append(it.name.padEnd(65))
                    append("${formatStr.format((it.length() / 1024.0))} kb\n")
                }
            }
        }
    }

    println(summary)
}

publishing {
    publications {
        create<MavenPublication>("kirc") {
            groupId = "de.cmdjulian"
            artifactId = "kirc"
            version = "1.0.0"

            from(components["java"])
            artifact(tasks["sourcesJar"])

            pom {
                packaging = "jar"
                name.set("kirc")
                description.set("GraalVM compatible sync / async container image registry client written in kotlin employing coroutines and fuel")
                url.set("https://github.com/cmdjulian/kirc")
                scm {
                    url.set("https://github.com/cmdjulian/kirc")
                }
                issueManagement {
                    url.set("https://github.com/cmdjulian/kirc/issues")
                }
                developers {
                    developer {
                        id.set("cmdjulian")
                        name.set("Julian Goede")
                    }
                }
            }
        }
    }
}
