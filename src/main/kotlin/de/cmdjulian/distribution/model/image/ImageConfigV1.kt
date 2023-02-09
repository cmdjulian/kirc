package de.cmdjulian.distribution.model.image

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.OffsetDateTime

// https://github.com/moby/moby/blob/master/image/spec/v1.2.md
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ImageConfigV1(
    val architecture: String,
    val variant: String?,
    val config: ContainerConfig,
    val container: String?,
    val containerConfig: ContainerConfig?,
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
    val rootfs: RootFs
) {
    companion object {
        const val MediaType = "application/vnd.docker.container.image.v1+json"
    }
}

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class ContainerConfig(
    val hostname: String?,
    val domainname: String?,
    val user: String?,
    val attachStdin: Boolean,
    val attachStdout: Boolean,
    val attachStderr: Boolean,
    val exposedPorts: Map<String, *>?,
    val tty: Boolean,
    val openStdin: Boolean,
    val stdinOnce: Boolean,
    val env: List<String>?,
    val cmd: List<String>?,
    val healthCheck: HealthCheck?,
    val argsEscaped: Boolean?,
    val image: String?,
    val volumes: Map<String, *>?,
    val workingDir: String?,
    val entrypoint: List<String>?,
    val onBuild: List<String>?,
    val labels: Map<String, String?>?,
    val stopSignal: String?,
    val stopTimeout: Int?,
    val shell: List<String>?
)

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class HealthCheck(
    val test: List<String>?,
    val startPeriod: Long?,
    val interval: Long?,
    val timeout: Long?,
    val retries: Int?
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class History(
    val created: OffsetDateTime?,
    val author: String?,
    val createdBy: String?,
    val emptyLayer: Boolean,
    val comment: String?
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class RootFs(val type: String, val diffIds: List<String>)
