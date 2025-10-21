package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.BlockingContainerImageClientFactory
import de.cmdjulian.kirc.client.BlockingContainerImageRegistryClient
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.testcontainer.RegistryTestContainer
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class RegistryBasicAuthTest {
    private lateinit var client: BlockingContainerImageRegistryClient
    private lateinit var registry: RegistryTestContainer
    private lateinit var cliHelper: DockerRegistryCliHelper

    private val helloWorldImage = javaClass.getResource("/hello-world.tar") ?: error("Resource not found")
    private val digestManifest = Digest("sha256:26c9f8a26a5f87d187957cf2d77efc7cf4d797e7fc55eee65316a0b62ae43034")
    private val digestConfig = Digest("sha256:74cc54e27dc41bb10dc4b2226072d469509f2f22f1a3ce74f4a59661a1d44602")
    private val digestLayer = Digest("sha256:63a41026379f4391a306242eb0b9f26dc3550d863b7fdbb97d899f6eb89efe72")
    private val digestDontKnow = Digest("sha256:3bb311b6ef0875d5ee696f572c59e0446844a4958ab9c3c766e47ac69682493f")

    @BeforeEach
    fun startRegistry() {
        registry = RegistryTestContainer().apply { start() }
        client = BlockingContainerImageClientFactory.create(registry.addressHttp.let(URI::create), credentials)
        cliHelper = DockerRegistryCliHelper(registry.addressHttp, credentials)
    }

    @AfterEach
    fun stopRegistry() {
        cliHelper.deleteAll()
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
        val repository = Repository("hello-world")

        cliHelper.pushImage(repository, Tag("latest"), helloWorldImage)

        val result = client.repositories().shouldNotBeEmpty().shouldHaveSize(1)
        result.first() shouldBe repository
    }

    @Test
    fun `config - get`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        cliHelper.pushImage(repository, tag, helloWorldImage)

        shouldNotThrowAny {
            client.config(repository, digestManifest)
        }
    }

    @Test
    fun `tags - contains uploaded image tag`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        cliHelper.pushImage(repository, tag, helloWorldImage)

        // contains tag and tag derived from manifest single digest
        client.tags(repository).shouldContain(tag)
        client.tags(repository).shouldContain(Tag(digestManifest.hash))
    }

    @Test
    fun `image - exists`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        cliHelper.pushImage(repository, tag, helloWorldImage)

        client.exists(repository, tag) shouldBe true
    }

    @Test
    fun `manifest - test for digest`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        val digest = cliHelper.pushImage(repository, tag, helloWorldImage)

        client.manifestDigest(repository, tag) shouldBe digest
    }

    @Test
    fun `manifest - delete by image digest`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        val digest = cliHelper.pushImage(repository, tag, helloWorldImage)

        client.tags(repository).shouldContain(tag)
        client.manifestDelete(repository, digest) shouldBe digest
        client.tags(repository).shouldNotContain(tag)
    }

    @Test
    fun `manifest - get as Manifest model`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")

        cliHelper.pushImage(repository, tag, helloWorldImage)

        val result = client.manifest(repository, tag).shouldBeInstanceOf<ManifestList>()
        result.manifests.shouldHaveSize(1)
    }

    @Test
    fun `blobs - exist`() {
        val repository = Repository("hello-world")
        val tag = Tag("latest")
        val blobDigests = listOf(digestManifest, digestConfig, digestLayer, digestDontKnow)

        cliHelper.pushImage(repository, tag, helloWorldImage)

        blobDigests.forEach { digest ->
            client.exists(repository, digest).shouldBeTrue()
        }
    }

    @Test
    fun `upload - to registry`() {
        val data = SystemFileSystem.source(Path(helloWorldImage.path))
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

        cliHelper.pushImage(repository, tag, helloWorldImage)

        val result = client.download(repository, tag)
        shouldNotThrowAny {
            // check if upload of downloaded data possible
            client.upload(Repository("test"), Tag("upload"), result)
        }
    }

    companion object {
        private val credentials = RegistryCredentials("changeIt", "changeIt")
    }
}
