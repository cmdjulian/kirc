package de.cmdjulian.kirc.impl.response

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
data class UploadSession(val sessionId: String, val location: String)