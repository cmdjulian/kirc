package de.cmdjulian.kirc.spec

import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag

/**
 *  Represents a `repositories` file inside the docker image.
 *
 *  Contains a Map of all [Repository]s included and its containing [Tag]s and layer name
 */
typealias Repositories = Map<Repository, Map<Tag, String>>
