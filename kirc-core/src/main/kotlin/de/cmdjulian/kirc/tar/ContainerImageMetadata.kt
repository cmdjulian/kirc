package de.cmdjulian.kirc.tar

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.Platform
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle

/**
 * Represents the metadata of an image tar archive without spilled blobs.
 *
 * @param index content of index.json
 * @param images collected manifests specified in index with their metadata
 */
data class ContainerImageMetadata(val index: ManifestList, val images: List<ContainerImageSingleMetadata>) {
    val imageForCurrentPlatform: ContainerImageSingleMetadata?
        get() = images.firstOrNull { image ->
            Platform(image.config.os, image.config.architecture) == Platform.current()
        }
}

/**
 * Represents a single platform image's metadata — no blob paths, just descriptors and deserialized config.
 *
 * @param manifest manifest of platform image
 * @param digest digest of platform image manifest
 * @param layers layer descriptors from the manifest (mediaType, size, digest)
 * @param config deserialized image config
 * @param tags human-readable tags for this image (e.g. "ubuntu:22.04"); empty if untagged.
 *             Resolved from Docker-format manifest.json repoTags and OCI index annotations
 *             (org.opencontainers.image.ref.name).
 */
data class ContainerImageSingleMetadata(
    val manifest: ManifestSingle,
    val digest: Digest,
    val layers: List<LayerReference>,
    val config: ImageConfig,
    val tags: List<String>,
)
