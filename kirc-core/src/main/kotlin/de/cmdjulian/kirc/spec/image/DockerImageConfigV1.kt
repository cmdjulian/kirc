package de.cmdjulian.kirc.spec.image

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import de.cmdjulian.kirc.spec.Architecture
import de.cmdjulian.kirc.spec.OS
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import io.goodforgod.graalvm.hint.annotation.ReflectionHint.AccessType
import java.time.OffsetDateTime

// https://github.com/moby/moby/blob/master/image/spec/v1.2.md
@ReflectionHint
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@ReflectionHint(AccessType.ALL_PUBLIC_CONSTRUCTORS, types = [PropertyNamingStrategies.SnakeCaseStrategy::class])
data class DockerImageConfigV1(
    val id: String?,
    val parent: String?,
    override val created: OffsetDateTime?,
    val dockerVersion: String?,
    val onBuild: List<String>,
    override val author: String?,
    override val architecture: Architecture,
    override val os: OS,
    val checksum: String?,
    @JsonProperty("Size") val size: Long?,
    override val config: ImageConfig.Config?,
    val container: String?,
    val containerConfig: ImageConfig.Config?,
    override val rootfs: RootFs,
    override val history: List<History>,
) : ImageConfig {
    @ReflectionHint
    companion object {
        const val MediaType = "application/vnd.docker.container.image.v1+json"
    }
}
