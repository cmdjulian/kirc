package de.cmdjulian.distribution.spec.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class HealthCheck(
    val test: List<String>,
    val startPeriod: Long?,
    val interval: Long?,
    val timeout: Long?,
    val retries: Int?,
)
