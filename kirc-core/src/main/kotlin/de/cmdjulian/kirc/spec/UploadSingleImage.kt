package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

/**
 * A container to hold data necessary for uploading an image
 */
@ReflectionHint
data class UploadSingleImage(val manifest: ManifestSingle, val digest: Digest, val blobs: List<LayerBlob>)

data class UploadContainerImage(
    val index: ManifestList,
    val images: List<UploadSingleImage>,
    val manifest: ManifestJson?,
    val repositories: Repositories?,
    val layout: OciLayout,
)