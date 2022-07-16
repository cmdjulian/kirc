package de.cmdjulian.distribution.model.manifest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import de.cmdjulian.distribution.model.manifest.Config
import de.cmdjulian.distribution.model.manifest.Layer

@JsonIgnoreProperties(value = ["schemaVersion"])
data class ManifestV2(val mediaType: String, val config: Config, val layers: List<Layer>)
