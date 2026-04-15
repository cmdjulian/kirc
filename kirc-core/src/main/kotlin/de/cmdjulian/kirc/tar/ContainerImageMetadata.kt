package de.cmdjulian.kirc.tar

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList

/**
 * Represents the metadata of an image tar archive without spilled blobs.
 *
 * @param index content of index.json
 * @param images collected manifests specified in index with their metadata
 * @param manifestJson content of OPTIONAL manifest.json
 * @param repositories content of OPTIONAL repositories file
 * @param layout content of oci-layout file
 */
data class ContainerImageMetadata(
    val index: ManifestList,
    val images: List<ContainerImageSingleMetadata>,
    val manifestJson: ManifestJson?,
    val repositories: Repositories?,
    val layout: OciLayout,
)

/**
 * Represents a single platform image's metadata — no blob paths, just descriptors and deserialized config.
 *
 * @param manifest manifest of platform image
 * @param digest digest of platform image manifest
 * @param layers layer descriptors from the manifest (mediaType, size, digest)
 * @param config deserialized image config
 */
data class ContainerImageSingleMetadata(
    val manifest: Manifest,
    val digest: Digest,
    val layers: List<LayerReference>,
    val config: ImageConfig,
)
