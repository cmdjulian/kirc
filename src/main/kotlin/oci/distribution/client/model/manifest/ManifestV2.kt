package oci.distribution.client.model.manifest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(value = ["schemaVersion"])
data class ManifestV2(val mediaType: String, val config: Config, val layers: List<Layer>)
