package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.ManifestJsonEntry
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1

object DockerArchiveHelper {

    fun createRepositories(repository: Repository, reference: Reference, vararg digests: Digest): Repositories {
        val tag = when (reference) {
            is Digest -> Tag.LATEST
            is Tag -> reference
        }

        val tagDigest = digests.associate { digest ->
            tag to digest.hash
        }

        return mapOf(repository to tagDigest)
    }

    fun createManifestJson(
        repository: Repository,
        reference: Reference,
        vararg images: ContainerImage,
    ): ManifestJson = images.map { image ->
        val layers = image.manifest.layers.map { layer -> "blobs/sha256/${layer.digest.hash}" }
        val layerSources = image.manifest.layers.associateBy(LayerReference::digest)
        val tag = when (reference) {
            is Digest -> Tag.LATEST
            is Tag -> reference
        }

        ManifestJsonEntry(
            config = "blobs/sha256/${image.manifest.config.digest.hash}",
            repoTags = listOf("$repository${tag.asImagePart()}"),
            layers = layers,
            layerSources = layerSources,
        )
    }

    fun createIndexForSingleImage(image: ContainerImage): ManifestList {
        val manifestListEntry = ManifestListEntry(
            mediaType = OciManifestListV1.MediaType,
            digest = image.digest,
            size = image.manifest.config.size + image.blobs.sumOf { it.data.size.toLong() },
            platform = ManifestListEntry.Platform(image.config.os, image.config.architecture, emptyList()),
        )
        return OciManifestListV1(2, OciManifestListV1.MediaType, listOf(manifestListEntry), mapOf())
    }
}