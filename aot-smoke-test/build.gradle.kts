import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    id("org.graalvm.buildtools.native") version "0.9.20"
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("de.cmdjulian.kirc.NativeImageSmokeTest")
    applicationDefaultJvmArgs = listOf("-agentlib:native-image-agent=config-output-dir=native-image")
}

dependencies {
    implementation(project(":kirc-core"))
    implementation(project(":kirc-image"))
    implementation(project(":kirc-blocking"))
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "${JavaVersion.VERSION_17}"
    }
}

graalvmNative {
    agent {
        defaultMode.set("standard")
    }
    toolchainDetection.set(false)
    binaries {
        all {
            resources.autodetect()
            buildArgs("--enable-url-protocols=https,http")
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}
