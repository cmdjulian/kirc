package de.cmdjulian.kirc.utils

import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import io.ktor.util.cio.toByteReadChannel
import kotlinx.io.Source
import kotlinx.io.asInputStream
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.path.pathString
import java.nio.file.Path as JavaPath
import kotlinx.io.files.Path as KotlinPath

internal fun JavaPath.toKotlinPath(): KotlinPath = KotlinPath(pathString)

internal fun Source.toByteReadChannel() = asInputStream().toByteReadChannel()

/**
 * Sanitize path parts for filesystem safety (':' and '/' replaced)
 *
 * Creates a path inside [tmpPath] with current timestamp, [repository], [reference] and optional [fileEnding].
 */
internal fun createSafePath(
    tmpPath: Path,
    repository: Repository,
    reference: Reference,
    fileEnding: String = "",
): Path {
    val timePart = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    val safePath = "$timePart--$repository--$reference$fileEnding"
        .replace(":", "-")
        .replace("/", "-")
        .replace("\\", "-")
    return Path.of(tmpPath.pathString, safePath)
}
