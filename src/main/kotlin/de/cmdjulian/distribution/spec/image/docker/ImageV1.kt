package de.cmdjulian.distribution.spec.image.docker

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import de.cmdjulian.distribution.spec.image.History
import de.cmdjulian.distribution.spec.image.ImageConfig
import de.cmdjulian.distribution.spec.image.RootFs
import java.time.OffsetDateTime

// https://github.com/moby/moby/blob/master/image/spec/v1.2.md
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ImageV1(
    val id: String?,
    val parent: String?,
    val created: OffsetDateTime?,
    val author: String?,
    val architecture: String,
    val os: String,
    val checksum: String?,
    @JsonProperty("Size") val size: UInt,
    val config: ImageConfig?,
    val rootfs: RootFs,
    val history: List<History>,
) {
    companion object {
        const val MediaType = "application/vnd.docker.container.image.v1+json"
    }
}
