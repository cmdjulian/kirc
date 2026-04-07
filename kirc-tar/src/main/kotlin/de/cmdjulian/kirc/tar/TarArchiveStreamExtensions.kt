@file:OptIn(InternalKircApi::class)

package de.cmdjulian.kirc.tar

import de.cmdjulian.kirc.annotation.InternalKircApi
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.impl.serialization.deserialize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.io.files.Path as KotlinPath

private fun Path.toKotlinPath(): KotlinPath = KotlinPath(pathString)

@InternalKircApi
suspend fun TarArchiveInputStream.readEntry(entry: TarArchiveEntry): ByteArray =
    withContext(Dispatchers.IO) { readNBytes(entry.size.toInt()) }

// We deserialize entries which aren't blobs. They are small enough to be loaded to memory
@InternalKircApi
suspend inline fun <reified T : Any> TarArchiveInputStream.deserializeEntry(entry: TarArchiveEntry): T =
    runCatching { JsonMapper.deserialize<T>(readEntry(entry)) }
        .getOrElse { throw IllegalStateException("Failed to deserialize tar entry '${entry.name}'", it) }

@InternalKircApi
suspend fun TarArchiveInputStream.processBlobEntry(entry: TarArchiveEntry, tempDirectory: Path): Path {
    val blobDigest = entry.name.removePrefix("blobs/sha256/")
    val tempPath = tempDirectory.resolve(blobDigest)
    withContext(Dispatchers.IO) {
        SystemFileSystem.sink(tempPath.toKotlinPath()).buffered().also { sink ->
            sink.write(this@processBlobEntry.asSource(), entry.size)
            sink.flush()
        }
    }
    return tempPath
}
