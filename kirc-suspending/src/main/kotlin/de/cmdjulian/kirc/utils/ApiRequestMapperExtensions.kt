package de.cmdjulian.kirc.utils

import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.result.map
import de.cmdjulian.kirc.impl.response.UploadSession
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered

internal fun ResponseResultOf<Unit>.mapToUploadSession() = third.map {
    UploadSession(
        sessionId = second["Docker-Upload-UUID"].single(),
        location = second["Location"].single(),
    )
}

class SourceDeserializer : Deserializable<Source> {
    override fun deserialize(response: Response): Source = response.body().toStream().asSource().buffered()
}