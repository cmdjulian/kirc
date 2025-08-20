package de.cmdjulian.kirc.spec

import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
data class OciLayout(val imageLayoutVersion: String = "1.0.0")