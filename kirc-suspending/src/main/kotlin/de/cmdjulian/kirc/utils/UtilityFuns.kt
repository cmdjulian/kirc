package de.cmdjulian.kirc.utils

import io.ktor.util.cio.toByteReadChannel
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlin.io.path.pathString
import kotlinx.io.files.Path as KotlinPath
import java.nio.file.Path as JavaPath

internal fun JavaPath.toKotlinPath(): KotlinPath = KotlinPath(pathString)

internal fun Source.toByteReadChannel() = asInputStream().toByteReadChannel()