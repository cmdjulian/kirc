package de.cmdjulian.kirc.tar

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import java.nio.file.Path

/**
 * Represents the whole content of an uploaded image tar archive
 *
 * @param index content of index.json
 * @param images collected manifests specified in index with their blobs
 * @param manifest content of OPTIONAL manifest.json
 * @param repositories content of OPTIONAL repositories file
 * @param layout content of oci-layout file
 */
data class ContainerImageTar(
    val index: ManifestList,
    val images: List<ContainerImageSingle>,
    val manifest: ManifestJson?,
    val repositories: Repositories?,
    val layout: OciLayout,
)

/**
 * Represents the content of a single platform image
 *
 * > config is handled as blob
 *
 * @param manifest manifest of platform image
 * @param digest digest of platform image manifest
 * @param blobs layer blobs + config blob ready for upload
 */
data class ContainerImageSingle(val manifest: Manifest, val digest: Digest, val blobs: List<BlobPath>)

/**
 * Represents the metadata of an image tar archive without spilled blobs.
 *
 * @param index content of index.json
 * @param images collected manifests specified in index with their metadata
 * @param manifest content of OPTIONAL manifest.json
 * @param repositories content of OPTIONAL repositories file
 * @param layout content of oci-layout file
 */
data class ContainerImageMetadata(
    val index: ManifestList,
    val images: List<ContainerImageSingleMetadata>,
    val manifest: ManifestJson?,
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

class BlobPath(val digest: Digest, val mediaType: String, val path: Path, val size: Long) {
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is BlobPath -> false
        mediaType != other.mediaType -> false
        digest != other.digest -> false
        path != other.path -> false
        size != other.size -> false
        else -> true
    }

    override fun hashCode(): Int {
        var result = mediaType.hashCode()
        result = 31 * result + digest.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }

    override fun toString(): String = "Blob(digest=$digest, mediaType='$mediaType', path=$path, size=$size)"
}
