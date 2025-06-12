package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

/**
 * A container to hold data necessary for uploading an image
 */
@ReflectionHint
data class UploadImage(val manifest: ManifestSingle, val digest: Digest, val blobs: List<LayerBlob>)
