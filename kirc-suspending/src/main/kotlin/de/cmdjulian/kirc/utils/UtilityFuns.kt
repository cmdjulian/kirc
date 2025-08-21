package de.cmdjulian.kirc.utils

import kotlin.io.path.pathString
import java.nio.file.Path as JavaPath
import kotlinx.io.files.Path as KotlinPath

internal fun JavaPath.toKotlinPath(): KotlinPath = KotlinPath(pathString)
