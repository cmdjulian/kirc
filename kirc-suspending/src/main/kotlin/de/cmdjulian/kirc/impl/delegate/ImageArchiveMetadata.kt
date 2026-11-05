package de.cmdjulian.kirc.impl.delegate

import de.cmdjulian.kirc.spec.ManifestJsonEntry
import de.cmdjulian.kirc.spec.image.ImageConfig
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle

/**
 * Metadata of an image archive, read in one pass by [ImageExtractor.metadata]
 *
 * @property index top level `index.json`, `null` if the archive has none
 * @property manifest manifest for the requested platform, resolved through nested indexes; `null` without index or
 * if the manifest blob is missing
 * @property config config of [manifest], `null` if [manifest] is `null` or its config blob is missing
 * @property manifestJson entries of the non-standard `manifest.json` written by `docker save`, `null` if absent
 */
data class ImageArchiveMetadata(
    val index: ManifestList?,
    val manifest: ManifestSingle?,
    val config: ImageConfig?,
    val manifestJson: List<ManifestJsonEntry>?,
)
