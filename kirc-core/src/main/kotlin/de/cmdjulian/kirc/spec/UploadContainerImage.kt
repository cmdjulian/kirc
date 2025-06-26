package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

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
data class UploadSingleImage(val manifest: ManifestSingle, val digest: Digest, val blobs: List<LayerBlob>)
