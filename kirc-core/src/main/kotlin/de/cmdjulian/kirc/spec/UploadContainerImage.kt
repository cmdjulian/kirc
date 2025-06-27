package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import java.nio.file.Path

/**
 * Represents the whole content of uploaded image
 *
 * @param index content of index.json
 * @param images collected manifests specified in index with their blobs
 * @param manifest content of OPTIONAL manifest.json
 * @param repositories content of OPTIONAL repositories file
 * @param layout content of oci-layout file
 */
@ReflectionHint
data class UploadContainerImage(
    val index: ManifestList,
    val images: List<UploadSingleImage>,
    val manifest: ManifestJson?,
    val repositories: Repositories?,
    val layout: OciLayout,
)

/**
 * Represents the content of a single platform image
 *
 * > config is handled as blob upon upload
 *
 * @param manifest manifest of platform image
 * @param digest digest of platform image manifest
 * @param blobs layer blobs + config blob ready for upload
 */
@ReflectionHint
data class UploadSingleImage(val manifest: ManifestSingle, val digest: Digest, val blobs: List<UploadBlobPath>)

@ReflectionHint
class UploadBlobPath(val digest: Digest, val mediaType: String, val path: Path) {
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is LayerBlob -> false
        mediaType != other.mediaType -> false
        digest != other.digest -> false
        else -> path == other.data
    }

    override fun hashCode(): Int {
        var result = mediaType.hashCode()
        result = 31 * result + digest.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }

    override fun toString(): String = "Blob(digest=$digest, mediaType='$mediaType', path=$path)"
}
