dependencies {
    implementation(project(":kirc-suspending"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
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
        archiveBaseName.set("kirc-blocking-${project.version}")
    }
}

publishing {
    publications {
        create<MavenPublication>("kirc-blocking") {
            groupId = "de.cmdjulian"
            artifactId = "kirc-blocking"
            version = project.version.toString()

            from(components["java"])
            artifact(tasks["sourcesJar"])

            pom {
                packaging = "jar"
                name.set("kirc-blocking")
                description.set("GraalVM compatible based container image registry client written in kotlin")
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
