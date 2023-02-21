package de.cmdjulian.distribution.spec.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.OffsetDateTime

sealed interface ImageConfig {
    val created: OffsetDateTime?
    val author: String?
    val architecture: String
    val os: String
    val config: Config?
    val rootfs: RootFs
    val history: List<History>

    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
    data class Config(
        val hostname: String?,
        val domainname: String?,
        val user: String?,
        val exposedPorts: Map<String, *>,
        val attachStdin: Boolean,
        val attachStdout: Boolean,
        val attachStderr: Boolean,
        val env: List<String>,
        val entrypoint: List<String>,
        val cmd: List<String>,
        val volumes: Map<String, *>,
        val workingDir: String?,
        val labels: Map<String, String>,
        val stopSignal: String?,
        val argsEscaped: Boolean?,
        val memory: Int?,
        val memorySwap: Int?,
        val cpuShares: Int?,
        val healthcheck: HealthCheck?,
    )
}
