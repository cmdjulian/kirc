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
        archiveBaseName.set("kirc-suspending-${project.version}")
    }
}

publishing {
    publications {
        create<MavenPublication>("kirc-suspending") {
            groupId = "de.cmdjulian.kirc"
            artifactId = "suspending"
            version = project.version.toString()

            from(components["java"])
            artifact(tasks["sourcesJar"])

            pom {
                packaging = "jar"
                name.set("kirc-suspending")
                description.set("GraalVM compatible coroutine based container image registry client written in kotlin")
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
