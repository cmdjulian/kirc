package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.utils.toByteReadChannel
import de.cmdjulian.kirc.utils.toKotlinPath
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a repeatable ktor request body.
 * Providing a [Path] allows to read the content multiple times.
 *
 * Important for when a request might be retried and the body needs to be sent again.
 * A [ByteReadChannel] can only be consumed once.
 * Therefore, by providing it directly to the request, the data is consumed after the first try and the second try fails.
 *
 * @param path the path to read the content from
 * @param ct the content type to set in the request
 */
internal class RepeatableFileContent(
    private val path: Path,
    private val ct: ContentType = ContentType.Application.OctetStream,
) : OutgoingContent.ReadChannelContent() {
    override val contentLength: Long = Files.size(path)
    override val contentType: ContentType = ct
    override fun readFrom(): ByteReadChannel =
        SystemFileSystem.source(path.toKotlinPath()).buffered().toByteReadChannel()
}
