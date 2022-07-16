package de.cmdjulian.distribution.model.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.OffsetDateTime

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class History(
    val created: OffsetDateTime?,
    val author: String?,
    val createdBy: String?,
    val emptyLayer: Boolean,
    val comment: String?
)
