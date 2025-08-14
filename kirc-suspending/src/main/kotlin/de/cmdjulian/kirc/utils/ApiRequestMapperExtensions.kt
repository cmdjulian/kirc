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
    val range = second["Range"].singleOrNull()?.split("-")
        ?: throw KircApiException("Range", first.url, first.method)
    val from = range.getOrNull(0)?.toLong() ?: throw KircApiException("Range[0]", first.url, first.method)
    val end = range.getOrNull(1)?.toLong() ?: throw KircApiException("Range[1]", first.url, first.method)

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