package de.cmdjulian.kirc.utils

import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.result.map
import de.cmdjulian.kirc.KircApiException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.impl.response.ResultSource
import de.cmdjulian.kirc.impl.response.UploadSession
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered

internal fun ResponseResultOf<Unit>.mapToUploadSession() = third.map {
    UploadSession(
        sessionId = second["Docker-Upload-UUID"].singleOrNull()
            ?: throw KircApiException("Docker-Upload-UUID", first.url, first.method),
        location = second["Location"].singleOrNull() ?: throw KircApiException("Location", first.url, first.method),
    )
}

internal fun ResponseResultOf<Unit>.mapToDigest() = third.map {
    second["Docker-Content-Digest"].singleOrNull()?.let(::Digest)
        ?: throw KircApiException("Docker-Content-Digest", first.url, first.method)
}

internal fun ResponseResultOf<Unit>.mapToRange() = third.map {
    val rangeHeader = second["Range"].singleOrNull() ?: throw KircApiException("Range", first.url, first.method)
    val rangeValue = if (rangeHeader.startsWith("bytes=")) rangeHeader.removePrefix("bytes=") else rangeHeader
    val rangeParts = rangeValue.split("-")
    val from = rangeParts.getOrNull(0)?.toLongOrNull() ?: throw KircApiException("Range[0]", first.url, first.method)
    val end = rangeParts.getOrNull(1)?.toLongOrNull() ?: throw KircApiException("Range[1]", first.url, first.method)

    from to end
}

internal fun ResponseResultOf<Source>.mapToResultSource() = third.map { source ->
    val size = second["Content-Length"].singleOrNull()?.toLong()
        ?: throw KircApiException("Content-Length", first.url, first.method)
    ResultSource(source, size)
}

class SourceDeserializer : Deserializable<Source> {
    override fun deserialize(response: Response): Source = response.body().toStream().asSource().buffered()
}
