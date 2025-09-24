package de.cmdjulian.kirc.impl.delegate

import de.cmdjulian.kirc.impl.jacksonDeserializer
import de.cmdjulian.kirc.utils.toKotlinPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.file.Path
import kotlin.io.path.pathString

internal suspend fun TarArchiveInputStream.readEntry(entry: TarArchiveEntry): ByteArray =
    withContext(Dispatchers.IO) { readNBytes(entry.size.toInt()) }

// We deserialize entries which aren't blobs. They are small enough to be loaded to memory
internal suspend inline fun <reified T : Any> TarArchiveInputStream.deserializeEntry(entry: TarArchiveEntry): T =
    jacksonDeserializer<T>().deserialize(readEntry(entry))

internal suspend fun TarArchiveInputStream.processBlobEntry(entry: TarArchiveEntry, tempDirectory: Path): Path {
    val blobDigest = entry.name.removePrefix("blobs/sha256/")
    val tempPath = Path.of(tempDirectory.pathString, blobDigest)
    withContext(Dispatchers.IO) {
        SystemFileSystem.sink(tempPath.toKotlinPath()).buffered().also { path ->
            path.write(this@processBlobEntry.asSource(), entry.size)
            path.flush()
        }
    }
    return tempPath
}