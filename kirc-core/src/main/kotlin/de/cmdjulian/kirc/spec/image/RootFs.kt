package de.cmdjulian.kirc.spec.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class RootFs(val type: String, val diffIds: List<String>)
