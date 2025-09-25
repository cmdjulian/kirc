package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.impl.delegate.ImageExtractor
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test
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
}
