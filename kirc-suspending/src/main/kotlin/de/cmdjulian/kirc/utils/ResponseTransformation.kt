package de.cmdjulian.kirc.utils

import com.github.kittinunf.result.Result
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.impl.KtorHttpError
import de.cmdjulian.kirc.impl.response.ResultSource
import de.cmdjulian.kirc.impl.response.UploadSession
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.io.asSource
import kotlinx.io.buffered

internal fun HttpResponse.toDigest(): Digest = headers[RegistryHeaders.DIGEST]?.let(::Digest)
    ?: throw KtorHttpError(
        status.value,
        request.url,
        request.method,
        ByteArray(0),
        IllegalStateException("Missing Docker-Content-Digest header"),
    )

internal suspend fun HttpResponse.toResultSource(): ResultSource {
    val size = headers[HttpHeaders.ContentLength]?.toLong()
        ?: throw KtorHttpError(
            status.value,
            request.url,
            request.method,
            ByteArray(0),
            IllegalStateException("Missing Content-Length"),
        )
    return ResultSource(bodyAsChannel().toInputStream().asSource().buffered(), size)
}

internal fun HttpResponse.toUploadSession(): UploadSession = UploadSession(
    sessionId = headers[RegistryHeaders.UPLOAD_UUID] ?: throw KtorHttpError(
        status.value,
        request.url,
        request.method,
        ByteArray(0),
        IllegalStateException("Missing Docker-Upload-UUID"),
    ),
    location = headers[HttpHeaders.Location] ?: throw KtorHttpError(
        status.value,
        request.url,
        request.method,
        ByteArray(0),
        IllegalStateException("Missing Location header"),
    ),
)

internal fun HttpResponse.toRange(): Pair<Long, Long> {
    val rangeHeader = headers["Range"] ?: throw KtorHttpError(
        status.value,
        request.url,
        request.method,
        ByteArray(0),
        IllegalStateException("Missing Range header"),
    )
    val rangeValue =
        if (rangeHeader.startsWith("bytes=")) rangeHeader.removePrefix("bytes=") else rangeHeader
    val parts = rangeValue.split('-')
    val from = parts.getOrNull(0)?.toLongOrNull() ?: throw KtorHttpError(
        status.value,
        request.url,
        request.method,
        ByteArray(0),
        IllegalStateException("Missing Range header, part 'from' is missing in \"bytes=<from>-<end>\""),
    )
    val end = parts.getOrNull(1)?.toLongOrNull() ?: throw KtorHttpError(
        status.value,
        request.url,
        request.method,
        ByteArray(0),
        IllegalStateException("Missing Range header, part 'end' is missing in \"bytes=<from>-<end>\""),
    )
    return Pair(from, end)
}

suspend inline fun <T, U, reified E : Throwable> Result<T, E>.mapSuspending(
    crossinline transform: suspend (T) -> U,
): Result<U, E> = try {
    when (this) {
        is Result.Success -> Result.success(transform(value))
        is Result.Failure -> Result.failure(error)
    }
} catch (ex: Exception) {
    when (ex) {
        is E -> Result.failure(ex)
        else -> throw ex
    }
}
