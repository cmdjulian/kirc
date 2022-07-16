package de.cmdjulian.distribution.model.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class Health(
    val test: List<String>?,
    val startPeriod: Long?,
    val interval: Long?,
    val timeout: Long?,
    val retries: Int?
)
