package de.cmdjulian.distribution.model

import de.cmdjulian.distribution.spec.manifest.Manifest as ManifestSpec
import de.cmdjulian.distribution.spec.manifest.ManifestList as ManifestListSpec

sealed interface ManifestResponse {
    @JvmInline
    value class Manifest(val manifest: ManifestSpec) : ManifestResponse

    @JvmInline
    value class ManifestList(val manifest: ManifestListSpec) : ManifestResponse
}
