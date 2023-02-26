dependencies {
    implementation(project(":kirc-image"))

    // jackson
    implementation(platform(jackson.bom))
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // tests
    testImplementation(platform(tests.junit.bom))
    testImplementation(tests.bundles.junit)
    testImplementation(tests.bundles.kotest)
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
        archiveBaseName.set("kirc-core-${project.version}")
    }
}

publishing {
    publications {
        create<MavenPublication>("kirc-core") {
            groupId = "de.cmdjulian.kirc"
            artifactId = "core"
            version = project.version.toString()

            from(components["java"])
            artifact(tasks["sourcesJar"])

            pom {
                packaging = "jar"
                name.set("kirc-suspending")
                description.set("kirc core components")
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
