package de.cmdjulian.kirc.impl

import com.adelean.inject.resources.junit.jupiter.GivenTextResource
import com.adelean.inject.resources.junit.jupiter.TestWithResources
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.Architecture
import de.cmdjulian.kirc.spec.OS
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.DockerManifestV2
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import de.cmdjulian.kirc.spec.manifest.OciManifestV1
import de.cmdjulian.kirc.unmarshal
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

@TestWithResources
internal class ManifestDeserializationTest {
    @Test
    fun dockerManifestListV1(@GivenTextResource("DockerManifestListV1.json") json: String) {
        assertSoftly(json.unmarshal<Manifest>()) {
            shouldBeInstanceOf<DockerManifestListV1>()
            schemaVersion shouldBe 2
            mediaType shouldBe "application/vnd.docker.distribution.manifest.list.v2+json"
            manifests shouldContainExactly listOf(
                ManifestListEntry(
                    "application/vnd.docker.distribution.manifest.v2+json",
                    Digest("sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f"),
                    7143,
                    ManifestListEntry.Platform(OS.LINUX, Architecture.PPC64LE, emptyList()),
                    emptyMap(),
                ),
                ManifestListEntry(
                    "application/vnd.docker.distribution.manifest.v2+json",
                    Digest("sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270"),
                    7682,
                    ManifestListEntry.Platform(OS.LINUX, Architecture.AMD64, listOf("sse4")),
                    emptyMap(),
                ),
            )
        }
    }

    @Test
    fun dockerManifestV2(@GivenTextResource("DockerManifestV2.json") json: String) {
        assertSoftly(json.unmarshal<Manifest>()) {
            shouldBeInstanceOf<DockerManifestV2>()
            schemaVersion shouldBe 2
            mediaType shouldBe "application/vnd.docker.distribution.manifest.v2+json"
            assertSoftly(config) {
                mediaType shouldBe "application/vnd.docker.container.image.v1+json"
                size shouldBe 7023
                digest shouldBe Digest("sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7")
            }
            layers shouldContainExactly listOf(
                LayerReference(
                    "application/vnd.docker.image.rootfs.diff.tar.gzip",
                    32654,
                    Digest("sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f"),
                ),
                LayerReference(
                    "application/vnd.docker.image.rootfs.diff.tar.gzip",
                    16724,
                    Digest("sha256:3c3a4604a545cdc127456d94e421cd355bca5b528f4a9c1905b15da2eb4a4c6b"),
                ),
                LayerReference(
                    "application/vnd.docker.image.rootfs.diff.tar.gzip",
                    73109,
                    Digest("sha256:ec4b8955958665577945c89419d1af06b5f7636b4ac3da7f12184802ad867736"),
                ),
            )
        }
    }

    @Test
    fun ociManifestListV1(@GivenTextResource("OciManifestListV1.json") json: String) {
        assertSoftly(json.unmarshal<Manifest>()) {
            shouldBeInstanceOf<OciManifestListV1>()
            schemaVersion shouldBe 2
            mediaType shouldBe "application/vnd.oci.image.index.v1+json"
            manifests shouldContainExactly listOf(
                ManifestListEntry(
                    "application/vnd.oci.image.manifest.v1+json",
                    Digest("sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f"),
                    7143,
                    ManifestListEntry.Platform(OS.LINUX, Architecture.PPC64LE, emptyList()),
                    emptyMap(),
                ),
                ManifestListEntry(
                    "application/vnd.oci.image.manifest.v1+json",
                    Digest("sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270"),
                    7682,
                    ManifestListEntry.Platform(OS.LINUX, Architecture.AMD64, emptyList()),
                    emptyMap(),
                ),
            )
            annotations
                .shouldNotBeNull()
                .shouldContainExactly(mapOf("com.example.key1" to "value1", "com.example.key2" to "value2"))
        }
    }

    @Test
    fun ociManifestV1(@GivenTextResource("OciManifestV1.json") json: String) {
        assertSoftly(json.unmarshal<Manifest>()) {
            shouldBeInstanceOf<OciManifestV1>()
            schemaVersion shouldBe 2
            mediaType shouldBe "application/vnd.oci.image.manifest.v1+json"
            assertSoftly(config) {
                mediaType shouldBe "application/vnd.oci.image.config.v1+json"
                size shouldBe 7023
                digest shouldBe Digest("sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7")
            }
            layers shouldContainExactly listOf(
                LayerReference(
                    "application/vnd.oci.image.layer.v1.tar+gzip",
                    32654,
                    Digest("sha256:9834876dcfb05cb167a5c24953eba58c4ac89b1adf57f28f2f9d09af107ee8f0"),
                ),
                LayerReference(
                    "application/vnd.oci.image.layer.v1.tar+gzip",
                    16724,
                    Digest("sha256:3c3a4604a545cdc127456d94e421cd355bca5b528f4a9c1905b15da2eb4a4c6b"),
                ),
                LayerReference(
                    "application/vnd.oci.image.layer.v1.tar+gzip",
                    73109,
                    Digest("sha256:ec4b8955958665577945c89419d1af06b5f7636b4ac3da7f12184802ad867736"),
                ),
            )
            assertSoftly(subject.shouldNotBeNull()) {
                mediaType shouldBe "application/vnd.oci.image.manifest.v1+json"
                size shouldBe 7682
                digest shouldBe Digest("sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270")
            }
            annotations
                .shouldNotBeNull()
                .shouldContainExactly(mapOf("com.example.key1" to "value1", "com.example.key2" to "value2"))
        }
    }
}
