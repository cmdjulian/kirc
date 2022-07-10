package oci.distribution.client.model.image

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.OffsetDateTime

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class History(val created: OffsetDateTime, val createdBy: String?, val emptyLayer: Boolean)
