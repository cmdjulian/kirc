package de.cmdjulian.distribution.impl.response

import de.cmdjulian.distribution.model.Tag

internal data class TagList(val name: String, val tags: List<Tag>)
