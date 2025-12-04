plugins {
    application
    id("org.graalvm.buildtools.native") version "0.11.2"
}

application {
    mainClass = "de.cmdjulian.kirc.NativeImageSmokeTest"
    applicationDefaultJvmArgs = listOf("-agentlib:native-image-agent=config-output-dir=native-image")
}

dependencies {
    implementation(project(":kirc-core"))
    implementation(project(":kirc-image"))
    implementation(project(":kirc-blocking"))
}

graalvmNative {
    agent {
        defaultMode = "standard"
    }
    toolchainDetection = false
    binaries {
        all {
            buildArgs("--enable-url-protocols=https,http")
        }
    }
    metadataRepository {
        enabled = true
    }
}
