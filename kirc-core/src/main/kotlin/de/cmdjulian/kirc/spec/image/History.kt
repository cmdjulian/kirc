package de.cmdjulian.kirc.spec.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import java.time.OffsetDateTime

@ReflectionHint
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class History(
    val created: OffsetDateTime?,
    val author: String?,
    val createdBy: String?,
    val emptyLayer: Boolean?,
    val comment: String?,
)
