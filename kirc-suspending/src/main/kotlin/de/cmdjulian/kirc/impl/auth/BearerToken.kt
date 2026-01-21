package de.cmdjulian.kirc.impl.auth

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.goodforgod.graalvm.hint.annotation.ReflectionHint
import java.time.OffsetDateTime

@ReflectionHint
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@ReflectionHint(types = [PropertyNamingStrategies.SnakeCaseStrategy::class])
data class BearerToken(
    @get:JsonAlias("access_token", "token")
    val token: String,
    val expiresIn: Long?,
    val issuedAt: OffsetDateTime?,
    val refreshToken: String?,
)
