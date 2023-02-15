import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.8.0"

    jacoco
    `java-library`
    `maven-publish`

    // Gradle task "dependencyCheckAnalyze" to check for security CVEs in dependencies
    id("org.owasp.dependencycheck") version "7.4.4"
    // check for dependency updates via task "dependencyUpdates --refresh-dependencies"
    id("com.github.ben-manes.versions") version "0.44.0"
    // linting
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    // licence scanning
    id("com.jaredsburrows.license") version "0.9.0"
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

kotlin {
    jvmToolchain {
        this.languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("com.github.haroldadmin:NetworkResponseAdapter:5.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("com.github.kittinunf.fuel:fuel:2.3.1")

    // Jackson
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.14.1"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Auth header parsing
    implementation("im.toss:http-auth-parser:0.1.2")

    // tests
    testImplementation(platform("org.junit:junit-bom:5.9.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    val kotest = "5.5.4"
    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotest")
    testImplementation("io.kotest:kotest-assertions-json-jvm:$kotest")
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
        archiveBaseName.set("jdsl-${project.version}")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

configure<KtlintExtension> {
    version.set("0.45.2")
    enableExperimentalRules.set(true)
}

tasks.register("depsize") {
    description = "Prints dependencies for \"default\" configuration"
    doLast {
        listConfigurationDependencies(configurations["default"])
    }
}

tasks.register("depsize-all-configurations") {
    description = "Prints dependencies for all available configurations"
    doLast {
        configurations
            .filter { it.isCanBeResolved }
            .forEach { listConfigurationDependencies(it) }
    }
}

fun listConfigurationDependencies(configuration: Configuration) {
    val formatStr = "%,10.2f"

    val size = configuration.sumOf { it.length() / (1024.0 * 1024.0) }

    val out = StringBuffer()
    out.append("\nConfiguration name: \"${configuration.name}\"\n")
    if (size > 0) {
        out.append("Total dependencies size:".padEnd(65))
        out.append("${String.format(formatStr, size)} Mb\n\n")

        configuration.sortedBy { -it.length() }
            .forEach {
                out.append(it.name.padEnd(65))
                out.append("${String.format(formatStr, (it.length() / 1024.0))} kb\n")
            }
    } else {
        out.append("No dependencies found")
    }
    println(out)
}

publishing {
    publications {
        create<MavenPublication>("distribution") {
            groupId = "de.cmdjulian"
            artifactId = "distribution"
            version = "1.0.2"

            from(components["java"])
            artifact(tasks["sourcesJar"])

            pom {
                packaging = "jar"
                name.set("distribution")
                description.set("sync / async docker registry client written in kotlin employing coroutines and retrofit")
                url.set("https://github.com/cmdjulian/docker-registry-client")
                scm {
                    url.set("https://github.com/cmdjulian/docker-registry-client")
                }
                issueManagement {
                    url.set("https://github.com/cmdjulian/docker-registry-client/issues")
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
