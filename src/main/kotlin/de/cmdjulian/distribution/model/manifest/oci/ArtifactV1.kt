package de.cmdjulian.distribution.model.manifest.oci

import de.cmdjulian.distribution.model.manifest.BlobReference

data class ArtifactV1(
    val mediaType: String,
    val artifactType: String,
    val blobs: List<BlobReference>,
    val subject: BlobReference? = null,
    val annotations: Map<String, String> = emptyMap()
) {
    companion object {
        const val MediaType = "application/vnd.oci.artifact.manifest.v1+json"
    }
}
