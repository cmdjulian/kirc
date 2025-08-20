package de.cmdjulian.kirc.utils

import de.cmdjulian.kirc.image.Digest
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import java.security.MessageDigest
import kotlin.io.path.pathString
import kotlin.math.min
import kotlinx.io.files.Path as KotlinPath
import java.nio.file.Path as JavaPath

internal fun sha256Digest(source: Source, size: Long): Digest {
    val md = MessageDigest.getInstance("SHA-256")
    val buffer = Buffer()
    var remaining = size
    while (remaining > 0) {
        val bytesRead = source.readAtMostTo(buffer, min(65536, remaining)).toInt()
        md.update(buffer.readByteArray(), 0, bytesRead)
        buffer.clear()
        remaining -= bytesRead
    }
    return ("sha256:" + md.digest().joinToString("") { "%02x".format(it) }).let(::Digest)
}

internal fun KotlinPath.toJavaPath(): JavaPath = JavaPath.of(name)

internal fun JavaPath.toKotlinPath(): KotlinPath = KotlinPath(pathString)