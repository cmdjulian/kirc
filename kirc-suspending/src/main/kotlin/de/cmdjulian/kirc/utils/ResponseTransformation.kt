package de.cmdjulian.kirc.utils

import com.github.kittinunf.result.Result
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.impl.KircApiError
import de.cmdjulian.kirc.impl.response.ResultSource
import de.cmdjulian.kirc.impl.response.UploadSession
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.io.asSource
import kotlinx.io.buffered

internal fun HttpResponse.toDigest(): Digest = headers["Docker-Content-Digest"]?.let(::Digest)
    ?: throw KircApiError.Header(status.value, request.url, request.method, "Header 'Docker-Content-Digest' is missing")

internal suspend fun HttpResponse.toResultSource(): ResultSource {
    val size = headers[HttpHeaders.ContentLength]?.toLong()
        ?: throw KircApiError.Header(
            status.value,
            request.url,
            request.method,
            "Header '${HttpHeaders.ContentLength}' is missing",
        )
    return ResultSource(bodyAsChannel().toInputStream().asSource().buffered(), size)
}

internal fun HttpResponse.toUploadSession(): UploadSession = UploadSession(
    sessionId = headers["Docker-Upload-UUID"] ?: throw KircApiError.Header(
        status.value,
        request.url,
        request.method,
        "Header 'Docker-Upload-UUID' is missing",
    ),
    location = headers[HttpHeaders.Location] ?: throw KircApiError.Header(
        status.value,
        request.url,
        request.method,
        "Header '${HttpHeaders.Location}' is missing",
    ),
)

internal fun HttpResponse.toRange(): Pair<Long, Long> {
    val rangeHeader = headers["Range"] ?: throw KircApiError.Header(
        status.value,
        request.url,
        request.method,
        "Header 'Range' is missing",
    )
    val rangeValue =
        if (rangeHeader.startsWith("bytes=")) rangeHeader.removePrefix("bytes=") else rangeHeader
    val parts = rangeValue.split('-')
    val from = parts.getOrNull(0)?.toLongOrNull() ?: throw KircApiError.Header(
        status.value,
        request.url,
        request.method,
        "Missing Range header, part 'from' is missing in \"bytes=<from>-<end>\"",

        )
    val end = parts.getOrNull(1)?.toLongOrNull() ?: throw KircApiError.Header(
        status.value,
        request.url,
        request.method,
        "Missing Range header, part 'end' is missing in \"bytes=<from>-<end>\"",
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
