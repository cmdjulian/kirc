package de.cmdjulian.kirc.impl.response

import de.cmdjulian.kirc.image.Repository
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
internal data class Catalog(val repositories: List<Repository>)
