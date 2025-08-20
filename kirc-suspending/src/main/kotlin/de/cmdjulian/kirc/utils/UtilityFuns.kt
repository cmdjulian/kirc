package de.cmdjulian.kirc.utils

import kotlin.io.path.pathString
import kotlinx.io.files.Path as KotlinPath
import java.nio.file.Path as JavaPath

internal fun JavaPath.toKotlinPath(): KotlinPath = KotlinPath(pathString)