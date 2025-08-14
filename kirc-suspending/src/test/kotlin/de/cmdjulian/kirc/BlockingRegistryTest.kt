package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.BlockingContainerImageClientFactory
import de.cmdjulian.kirc.client.BlockingContainerImageRegistryClient
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class BlockingRegistryTest {
    private lateinit var client: BlockingContainerImageRegistryClient
    private lateinit var registry: RegistryTestContainer

    private val digestManifest = Digest("sha256:26c9f8a26a5f87d187957cf2d77efc7cf4d797e7fc55eee65316a0b62ae43034")
    private val digestConfig = Digest("sha256:74cc54e27dc41bb10dc4b2226072d469509f2f22f1a3ce74f4a59661a1d44602")
    private val digestLayer = Digest("sha256:63a41026379f4391a306242eb0b9f26dc3550d863b7fdbb97d899f6eb89efe72")
    private val digestDontKnow = Digest("sha256:3bb311b6ef0875d5ee696f572c59e0446844a4958ab9c3c766e47ac69682493f")
    private val digestImage = Digest("sha256:7565f2c7034d87673c5ddc3b1b8e97f8da794c31d9aa73ed26afffa1c8194889")

    init {
        DockerRegistryCliHelper.loadImage(HELLO_WORLD_IMAGE, "hello-world:latest")
    }

    @BeforeEach
    fun startRegistry() {
        registry = RegistryTestContainer().apply { start() }
        client = BlockingContainerImageClientFactory.create(registry.addressHttp.let(URI::create), credentials)
        DockerRegistryCliHelper.login(registry.addressName, credentials)
    }

    @AfterEach
    fun stopRegistry() {
        registry.stop()
    }

    @Test
    fun `testConnection - ping to registry`() {
        shouldNotThrowAny {
            client.testConnection()
        }
        client.repositories().shouldBeEmpty()
    }

    @Test
    fun `repositories - returns correct amount of repos`() {
        client.repositories().shouldBeEmpty()

        DockerRegistryCliHelper.pushImage(registry.addressName, "hello-world:latest")

        val result = client.repositories().shouldNotBeEmpty().shouldHaveSize(1)
        result.first() shouldBe Repository("hello-world")
    }

    @Test
    fun `config - get`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        DockerRegistryCliHelper.pushImage(registry.addressName, "$repository:$tag")

        shouldNotThrowAny {
            client.config(repository, tag)
        }
    }

    @Test
    fun `tags - contains uploaded image tag`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        DockerRegistryCliHelper.pushImage(registry.addressName, "$repository:$tag")

        client.tags(repository).shouldHaveSize(1).first() shouldBe tag
    }

    @Test
    fun `image - exists`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        DockerRegistryCliHelper.pushImage(registry.addressName, "$repository:$tag")

        client.exists(repository, tag) shouldBe true
    }

    @Test
    fun `manifest - test for digest`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")
        val digest = Digest("sha256:7565f2c7034d87673c5ddc3b1b8e97f8da794c31d9aa73ed26afffa1c8194889")

        DockerRegistryCliHelper.pushImage(registry.addressName, "$repository:$tag")

        client.manifestDigest(repository, tag) shouldBe digest
    }

    @Test
    fun `manifest - delete by image digest`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        DockerRegistryCliHelper.pushImage(registry.addressName, "$repository:$tag")

        client.manifestDelete(repository, digestImage) shouldBe digestImage
        client.tags(repository).shouldBeEmpty()
    }

    @Test
    fun `manifest - get as Manifest model`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        DockerRegistryCliHelper.pushImage(registry.addressName, "$repository:$tag")

        val result = client.manifest(repository, tag).shouldBeInstanceOf<ManifestSingle>()
        result.layers.shouldNotBeEmpty()
    }

    @Test
    fun `blobs - exist`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")
        val blobDigests = listOf(digestManifest, digestConfig, digestLayer, digestDontKnow)

        DockerRegistryCliHelper.pushImage(registry.addressName, "$repository:$tag")

        blobDigests.forEach { digest ->
            client.exists(repository, digest).shouldBeTrue()
        }
    }

    @Test
    fun `upload - to registry`() {
        val data = SystemFileSystem.source(Path(HELLO_WORLD_IMAGE))
        val repository = Repository("python")
        val tag = Tag("test")

        client.exists(repository, tag) shouldBe false

        shouldNotThrowAny {
            client.upload(repository, tag, data.buffered().asInputStream())
        }

        client.exists(repository, tag) shouldBe true
    }

    @Test
    fun `download - from registry`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        DockerRegistryCliHelper.pushImage(registry.addressName, "$repository:$tag")

        val result = client.download(repository, tag)
        shouldNotThrowAny {
            // check if upload of downloaded data possible
            client.upload(Repository("test"), Tag("upload"), result)
        }
    }

    companion object {
        private val credentials = RegistryCredentials("changeIt", "changeIt")
        private const val HELLO_WORLD_IMAGE = "src/test/resources/hello-world.tar"

        @JvmStatic
        @AfterAll
        fun cleanup() {
            DockerRegistryCliHelper.removeAllTestImages()
            DockerRegistryCliHelper.logoutFromAllTestRegistries()
        }
    }
}
