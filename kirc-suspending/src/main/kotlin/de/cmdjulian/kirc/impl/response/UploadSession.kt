package de.cmdjulian.kirc.impl.response

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

// todo remove reflection hint: only for mapping necessary
@ReflectionHint
//@ConsistentCopyVisibility
data class UploadSession /*internal constructor*/(val sessionId: String, val location: String)
