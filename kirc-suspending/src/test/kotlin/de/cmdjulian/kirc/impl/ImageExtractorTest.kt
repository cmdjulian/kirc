package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.tar.ImageExtractor
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

internal class ImageExtractorTest {

    private val resource = javaClass.getResource("/hello-world.tar") ?: error("Resource not found")
    private val path = Path(resource.path)

    @Test
    fun `extract index`() = runTest {
        ImageExtractor.parse(path).index.manifests.shouldHaveSize(1)
    }

    @Test
    fun `extract repositories`() = runTest {
        ImageExtractor.parse(path).repositories.shouldNotBeNull().shouldHaveSize(1)
    }

    @Test
    fun `extract manifest json`() = runTest {
        ImageExtractor.parse(path).manifest.shouldNotBeNull().shouldBeSingleton {
            it.layers.shouldHaveSize(1)
            it.layerSources.shouldHaveSize(1)
            it.repoTags.shouldHaveSize(1)
        }
    }

    @Test
    fun `extract manifest - by digest`() = runTest {
        val digest = Digest("sha256:26c9f8a26a5f87d187957cf2d77efc7cf4d797e7fc55eee65316a0b62ae43034")
        val metadata = ImageExtractor.parse(path)
        metadata.images.firstOrNull { it.digest == digest }.shouldNotBeNull()
    }

    @Test
    fun `extract manifest - by index`() = runTest {
        val metadata = ImageExtractor.parse(path)
        metadata.images.shouldBeSingleton()
    }

    @Test
    fun `extract config - by digest`() = runTest {
        val digest = Digest("sha256:74cc54e27dc41bb10dc4b2226072d469509f2f22f1a3ce74f4a59661a1d44602")
        val metadata = ImageExtractor.parse(path)
        metadata.images.firstOrNull {
            (it.manifest as? ManifestSingle)?.config?.digest == digest
        }?.config.shouldNotBeNull()
    }

    @Test
    fun `extract config - by manifest`() = runTest {
        val metadata = ImageExtractor.parse(path)
        metadata.images.shouldBeSingleton { it.config.shouldNotBeNull() }
    }
}
