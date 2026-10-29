package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.impl.delegate.ImageExtractor
import de.cmdjulian.kirc.spec.Architecture
import de.cmdjulian.kirc.spec.OS
import de.cmdjulian.kirc.spec.Platform
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.io.path.Path

internal class ImageExtractorTest {

    private val resource = javaClass.getResource("/hello-world.tar") ?: error("Resource not found")
    private val extractor = ImageExtractor(Path(resource.path))

    @Test
    fun `extract index`() {
        extractor.index().shouldNotBeNull().manifests.shouldHaveSize(1)
    }

    @Test
    fun `extract repositories`() {
        val result = extractor.repositoriesJson().shouldNotBeNull()
        result.shouldHaveSize(1)
    }

    @Test
    fun `extract manifest json`() {
        val result = extractor.manifestJson().shouldNotBeNull()
        result.shouldBeSingleton {
            it.layers.shouldHaveSize(1)
            it.layerSources.shouldHaveSize(1)
            it.repoTags.shouldHaveSize(1)
        }
    }

    @Test
    fun `extract manifest - by digest`() {
        val digest = Digest("sha256:26c9f8a26a5f87d187957cf2d77efc7cf4d797e7fc55eee65316a0b62ae43034")
        extractor.manifest(digest).shouldNotBeNull()
    }

    @Test
    fun `extract manifest - by index`() {
        val index = extractor.index().shouldNotBeNull()
        extractor.manifest(index).shouldNotBeNull()
    }

    @Test
    fun `extract config - by digest`() {
        val digest = Digest("sha256:74cc54e27dc41bb10dc4b2226072d469509f2f22f1a3ce74f4a59661a1d44602")
        extractor.config(digest).shouldNotBeNull()
    }

    @Test
    fun `extract config - by manifest`() {
        val index = extractor.index().shouldNotBeNull()
        val manifest = extractor.manifest(index).shouldNotBeNull()
        extractor.config(manifest).shouldNotBeNull()
    }

    @Test
    fun `extract blob - returns raw bytes`() {
        val digest = Digest("sha256:26c9f8a26a5f87d187957cf2d77efc7cf4d797e7fc55eee65316a0b62ae43034")
        val blob = extractor.blob(digest).shouldNotBeNull()

        val hash = MessageDigest.getInstance("SHA-256").digest(blob).toHexString()
        Digest("sha256:$hash") shouldBe digest
    }

    /**
     * `index.json` points to a nested image index, as written by the containerd image store, which lists an
     * attestation manifest (`unknown/unknown`) before the `linux/amd64` image manifest
     */
    @Nested
    inner class NestedIndex {
        private val resource = javaClass.getResource("/hello-world-nested-index.tar") ?: error("Resource not found")
        private val extractor = ImageExtractor(Path(resource.path))
        private val linuxAmd64 = Platform(OS.LINUX, Architecture.AMD64)
        private val imageManifestDigest =
            Digest("sha256:26c9f8a26a5f87d187957cf2d77efc7cf4d797e7fc55eee65316a0b62ae43034")

        @Test
        fun `extract index - by digest`() {
            val digest = extractor.index().shouldNotBeNull().manifests.single().digest

            extractor.index(digest).shouldNotBeNull().manifests.shouldHaveSize(2)
        }

        @Test
        fun `extract manifest - by index skips attestation`() {
            val index = extractor.index().shouldNotBeNull()

            extractor.manifest(index, linuxAmd64) shouldBe extractor.manifest(imageManifestDigest)
        }

        @Test
        fun `extract config - by manifest`() {
            val index = extractor.index().shouldNotBeNull()
            val manifest = extractor.manifest(index, linuxAmd64).shouldNotBeNull()

            extractor.config(manifest).shouldNotBeNull()
        }

        @Test
        fun `extract manifest - by index without matching platform`() {
            val index = extractor.index().shouldNotBeNull()

            shouldThrow<KircException.PlatformNotMatching> {
                extractor.manifest(index, Platform(OS.LINUX, Architecture.ARM64))
            }
        }
    }
}
