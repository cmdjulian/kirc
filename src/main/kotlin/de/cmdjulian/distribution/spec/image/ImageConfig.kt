package de.cmdjulian.distribution.spec.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class ImageConfig(
    val user: String?,
    val exposedPorts: Map<String, *>,
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
