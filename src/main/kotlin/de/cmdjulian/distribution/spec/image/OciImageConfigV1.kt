package de.cmdjulian.distribution.spec.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import de.cmdjulian.distribution.spec.Architecture
import de.cmdjulian.distribution.spec.OS
import java.time.OffsetDateTime

// https://github.com/opencontainers/image-spec/blob/main/config.md
@JsonNaming(PropertyNamingStrategies.LowerDotCaseStrategy::class)
data class OciImageConfigV1(
    override val created: OffsetDateTime?,
    override val author: String?,
    override val architecture: Architecture,
    override val os: OS,
    val osVersion: String?,
    val osFeatures: List<String>?,
    val variant: String?,
    override val config: ImageConfig.Config?,
    override val rootfs: RootFs,
    override val history: List<History>?,
) : ImageConfig {
    companion object {
        const val MediaType = "application/vnd.oci.image.config.v1+json"
    }
}
