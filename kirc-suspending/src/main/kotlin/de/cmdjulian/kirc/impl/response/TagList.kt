package de.cmdjulian.kirc.impl.response

import de.cmdjulian.kirc.image.Tag
import io.goodforgod.graalvm.hint.annotation.ReflectionHint

@ReflectionHint
internal data class TagList(val name: String, val tags: List<Tag>)
