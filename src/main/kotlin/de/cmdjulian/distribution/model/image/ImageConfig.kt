package de.cmdjulian.distribution.model.image

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ImageConfig(

    val architecture: String,

    val variant: String?,

    val config: Config,

    val container: String?,

    val containerConfig: Config?,

    val created: OffsetDateTime?,

    val dockerVersion: String?,

    val id: String?,

    val os: String,

    val parent: String?,

    val throwaway: Boolean,

    val author: String?,

    val comment: String?,

    val size: Long,

    val history: List<History>?,

    val rootfs: RootFs,

    @JsonProperty("moby.buildkit.buildinfo.v1")
    val buildkitBuildInfo: String?,

    @JsonProperty("moby.buildkit.cache.v0")
    val buildkitCache: String?,

)
