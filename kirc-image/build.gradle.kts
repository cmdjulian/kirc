tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
    }
}

kotlinPublications {
    publication {
        publicationName.set("image")
        artifactId.set("image")
        description.set("kirc module for parsing container image name and components")
    }
}
