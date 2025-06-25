package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.ManifestJsonEntry
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1

object DockerArchiveHelper {

    fun createRepositoriesFile(repository: Repository, reference: Reference, vararg digests: Digest): Repositories {
        val tag = when (reference) {
            is Digest -> Tag.LATEST
            is Tag -> reference
        }

        val tagDigest = buildMap {
            digests.forEach { digest -> put(tag, digest.hash) }
        }

        return mapOf(repository to tagDigest)
    }

    // todo DONNERSTAG!!!
    fun createManifestJsonFile(
        repository: Repository,
        reference: Reference,
        image: ContainerImage,
    ): ManifestJsonEntry {
        val layers = image.manifest.layers.map { layer -> "blobs/sha256/${layer.digest.hash}" }
        val layerSources = image.manifest.layers.associateBy(LayerReference::digest)

        return ManifestJsonEntry(
            config = "blobs/sha256/${image.manifest.config.digest.hash}",
            // todo is this okay or rather Tag.LATEST if reference is no tag
            repoTags = listOf("$repository${reference.asImagePart()}"),
            layers = layers,
            layerSources = layerSources,
        )
    }

    fun createIndexFile(image: ContainerImage): ManifestList {
        val manifestListEntry = ManifestListEntry(
            mediaType = "",
            digest = image.digest,
            size = 2,
            platform = ManifestListEntry.Platform(image.config.os, image.config.architecture, emptyList()),
        )
        return OciManifestListV1(2, OciManifestListV1.MediaType, listOf(manifestListEntry), mapOf())
    }
}