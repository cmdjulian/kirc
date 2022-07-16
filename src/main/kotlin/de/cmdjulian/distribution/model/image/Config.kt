package de.cmdjulian.distribution.model.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class Config(

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

    val healthCheck: Health?,

    val argsEscaped: Boolean?,

    val image: String?,

    val volumes: Map<String, Any?>?,

    val workingDir: String?,

    val entrypoint: List<String>?,

    val onBuild: List<String>?,

    val labels: Map<String, String?>?,

    val stopSignal: String?,

    val stopTimeout: Int?,

    val shell: List<String>?

)
