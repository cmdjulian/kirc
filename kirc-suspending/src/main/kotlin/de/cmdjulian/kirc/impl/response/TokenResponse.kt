package de.cmdjulian.kirc.impl.response

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@ReflectionHint(types = [PropertyNamingStrategies.SnakeCaseStrategy::class])
internal class TokenResponse(
    @get:JsonAlias("access_token", "token")
    val token: String,
    val expiresIn: Long?,
    val issuedAt: String?,
    val refreshToken: String?,
)