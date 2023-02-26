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
        archiveBaseName.set("kirc-image-${project.version}")
    }
}

publishing {
    publications {
        create<MavenPublication>("kirc-models") {
            groupId = "de.cmdjulian.kirc"
            artifactId = "image"
            version = project.version.toString()

            from(components["java"])
            artifact(tasks["sourcesJar"])

            pom {
                packaging = "jar"
                name.set("kirc-suspending")
                description.set("kirc module for parsing container image name and components")
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
