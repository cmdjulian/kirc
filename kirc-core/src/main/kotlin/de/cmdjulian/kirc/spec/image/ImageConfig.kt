package de.cmdjulian.kirc.spec.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import de.cmdjulian.kirc.spec.Architecture
import de.cmdjulian.kirc.spec.OS
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import java.time.OffsetDateTime

@ReflectionHint
sealed interface ImageConfig {
    val created: OffsetDateTime?
    val author: String?
    val architecture: Architecture
    val os: OS
    val config: Config?
    val rootfs: RootFs
    val history: List<History>

    @ReflectionHint
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
    data class Config(
        val hostname: String?,
        val domainname: String?,
        val user: String?,
        val exposedPorts: Map<String, *>,
        val attachStdin: Boolean?,
        val attachStdout: Boolean?,
        val attachStderr: Boolean?,
        val tty: Boolean?,
        val openStdin: Boolean?,
        val stdinOnce: Boolean?,
        val env: List<String>,
        val entrypoint: List<String>,
        val cmd: List<String>,
        val image: String?,
        val volumes: Map<String, *>,
        val workingDir: String?,
        val labels: Map<String, String>,
        val stopSignal: String?,
        val argsEscaped: Boolean?,
        val memory: Long?,
        val memorySwap: Long?,
        val cpuShares: Long?,
        val healthcheck: HealthCheck?,
    )
}
