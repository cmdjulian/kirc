package de.cmdjulian.distribution.spec.image

import com.adelean.inject.resources.junit.jupiter.GivenTextResource
import com.adelean.inject.resources.junit.jupiter.TestWithResources
import org.junit.jupiter.api.Test

@TestWithResources
internal class ImageConfigDeserializationTest {
    @Test
    fun ociImageConfigV1(@GivenTextResource("OciImageConfigV1.json") json: String) {
        TODO()
    }

    @Test
    fun dockerImageConfigV1(@GivenTextResource("DockerImageConfigV1.json") json: String) {
        TODO()
    }
}