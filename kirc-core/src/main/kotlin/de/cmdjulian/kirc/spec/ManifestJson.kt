package de.cmdjulian.kirc.spec

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.manifest.LayerReference
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

// We use an array, otherwise deserialization doesn't work as intended
typealias ManifestJson = Array<ManifestJsonEntry>

/**
 * Represents the `manifest.json` file inside a docker archive.
 *
 * NOT PART OF the OFFICIAL OCI STANDARD!
 */
@ReflectionHint
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
@ReflectionHint(types = [PropertyNamingStrategies.UpperCamelCaseStrategy::class])
data class ManifestJsonEntry(
    val config: String,
    val repoTags: List<String>,
    val layers: List<String>,
    val layerSources: Map<Digest, LayerReference>,
)
