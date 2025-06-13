package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import kotlinx.coroutines.Deferred

/**
 * A container to hold data for writing downloaded data into a gzip file
 */
@ReflectionHint
data class DownloadImage(
    val manifest: ManifestSingle,
    val digest: Digest,
    val config: ImageConfig,
    val blobs: List<DeferredImageBlob>,
) {
    data class DeferredImageBlob(val digest: Digest, val deferred: Deferred<ByteArray>)
}