package de.cmdjulian.kirc.spec.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import io.goodforgod.graalvm.hint.annotation.ReflectionHint.AccessType

@ReflectionHint
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
@ReflectionHint(AccessType.ALL_PUBLIC_CONSTRUCTORS, types = [PropertyNamingStrategies.UpperCamelCaseStrategy::class])
data class HealthCheck(
    val test: List<String>,
    val startPeriod: Long?,
    val interval: Long?,
    val timeout: Long?,
    val retries: Int?,
)
