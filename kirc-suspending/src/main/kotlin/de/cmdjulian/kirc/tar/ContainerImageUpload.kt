package de.cmdjulian.kirc.tar

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
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
internal data class ContainerImageUpload(
    val index: ManifestList,
    val images: List<UploadImage>,
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
internal data class UploadImage(val manifest: Manifest, val digest: Digest, val blobs: List<BlobPath>)

internal class BlobPath(val digest: Digest, val mediaType: String, val path: Path, val size: Long) {
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
