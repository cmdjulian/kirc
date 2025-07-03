package de.cmdjulian.kirc.utils

import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.result.map
import de.cmdjulian.kirc.impl.response.UploadSession

internal fun ResponseResultOf<Unit>.mapToUploadSession() = third.map {
    UploadSession(
        sessionId = second["Docker-Upload-UUID"].single(),
        location = second["Location"].single(),
    )
}