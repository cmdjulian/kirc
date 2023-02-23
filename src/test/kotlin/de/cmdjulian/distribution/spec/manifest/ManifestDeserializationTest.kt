package de.cmdjulian.distribution.spec.manifest

import de.cmdjulian.distribution.model.Digest
import de.cmdjulian.distribution.spec.unmarshal
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class ManifestDeserializationTest {
    @Test
    fun `deserialize OciManifestV1`() {
        @Language("json")
        val json = """
            {
                "schemaVersion": 2,
                "mediaType": "application/vnd.oci.image.manifest.v1+json",
                "config": {
                    "mediaType": "application/vnd.oci.image.config.v1+json",
                    "size": 7023,
                    "digest": "sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7"
                },
                "layers": [
                    {
                        "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                        "size": 32654,
                        "digest": "sha256:9834876dcfb05cb167a5c24953eba58c4ac89b1adf57f28f2f9d09af107ee8f0"
                    },
                    {
                        "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                        "size": 16724,
                        "digest": "sha256:3c3a4604a545cdc127456d94e421cd355bca5b528f4a9c1905b15da2eb4a4c6b"
                    },
                    {
                        "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                        "size": 73109,
                        "digest": "sha256:ec4b8955958665577945c89419d1af06b5f7636b4ac3da7f12184802ad867736"
                    }
                ],
                "subject": {
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "size": 7682,
                    "digest": "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270"
                },
                "annotations": {
                    "com.example.key1": "value1",
                    "com.example.key2": "value2"
                }
            }
        """.trimIndent()

        val manifest = json.unmarshal<Manifest>()

        assertSoftly(manifest) {
            shouldBeInstanceOf<OciManifestV1>()
            schemaVersion shouldBe 2u
            mediaType shouldBe "application/vnd.oci.image.manifest.v1+json"
            assertSoftly(config) {
                mediaType shouldBe "application/vnd.oci.image.config.v1+json"
                size shouldBe 7023u
                digest shouldBe Digest("sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7")
            }
            layers shouldContainExactly listOf(
                LayerReference(
                    "application/vnd.oci.image.layer.v1.tar+gzip",
                    32654u,
                    Digest("sha256:9834876dcfb05cb167a5c24953eba58c4ac89b1adf57f28f2f9d09af107ee8f0"),
                ),
                LayerReference(
                    "application/vnd.oci.image.layer.v1.tar+gzip",
                    16724u,
                    Digest("sha256:3c3a4604a545cdc127456d94e421cd355bca5b528f4a9c1905b15da2eb4a4c6b"),
                ),
                LayerReference(
                    "application/vnd.oci.image.layer.v1.tar+gzip",
                    73109u,
                    Digest("sha256:ec4b8955958665577945c89419d1af06b5f7636b4ac3da7f12184802ad867736"),
                ),
            )
            assertSoftly(subject.shouldNotBeNull()) {
                mediaType shouldBe "application/vnd.oci.image.manifest.v1+json"
                size shouldBe 7682u
                digest shouldBe Digest("sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270")
            }
            annotations shouldContainExactly mapOf("com.example.key1" to "value1", "com.example.key2" to "value2")
        }
    }

    @Test
    fun `deserialize OciManifestList`() {
        @Language("JSON") val json = """
            {
                "schemaVersion": 2,
                "mediaType": "application/vnd.oci.image.index.v1+json",
                "manifests": [
                    {
                        "mediaType": "application/vnd.oci.image.manifest.v1+json",
                        "size": 7143,
                        "digest": "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
                        "platform": {
                            "architecture": "ppc64le",
                            "os": "linux"
                        }
                    },
                    {
                        "mediaType": "application/vnd.oci.image.manifest.v1+json",
                        "size": 7682,
                        "digest": "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270",
                        "platform": {
                            "architecture": "amd64",
                            "os": "linux"
                        }
                    }
                ],
                "annotations": {
                    "com.example.key1": "value1",
                    "com.example.key2": "value2"
                }
            }
        """.trimIndent()

        val index = json.unmarshal<Manifest>()

        assertSoftly(index) {
            shouldBeInstanceOf<OciManifestListV1>()
            schemaVersion shouldBe 2u
            mediaType shouldBe "application/vnd.oci.image.index.v1+json"
            manifests shouldContainExactly listOf(
                ManifestListEntry(
                    "application/vnd.oci.image.manifest.v1+json",
                    Digest("sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f"),
                    7143u,
                    ManifestListEntry.Platform("linux", "ppc64le"),
                ),
                ManifestListEntry(
                    "application/vnd.oci.image.manifest.v1+json",
                    Digest("sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270"),
                    7682u,
                    ManifestListEntry.Platform("linux", "amd64"),
                ),
            )
            annotations shouldContainExactly mapOf("com.example.key1" to "value1", "com.example.key2" to "value2")
        }
    }
}
