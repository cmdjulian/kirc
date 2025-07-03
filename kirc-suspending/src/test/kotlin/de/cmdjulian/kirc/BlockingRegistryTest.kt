package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.BlockingContainerImageClientFactory
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.client.SuspendingContainerImageClientFactory
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Paths
import kotlin.io.path.inputStream

internal class BlockingRegistryTest {
    private val client =
        BlockingContainerImageClientFactory.create(registry.address.let(URI::create), credentials)
    private val suspendingClient =
        SuspendingContainerImageClientFactory.create(registry.address.let(URI::create), credentials)

    @Test
    fun testConnection() {
        client.testConnection()
        client.repositories().shouldBeEmpty()
    }

    @Test
    fun testSuspendingUpload() = runTest {
        val data =
            _root_ide_package_.kotlinx.io.files.SystemFileSystem.source("src/test/resources/test-image.small.tar".let(::Path))
                .buffered()
        val repository = Repository("python")
        val tag = Tag("test")

        suspendingClient.exists(repository, tag) shouldBe false

        shouldNotThrowAny {
            suspendingClient.upload(repository, tag, data)
        }

        suspendingClient.exists(repository, tag) shouldBe true
    }

    @Test
    fun testUploadSmall() {
        val data = Paths.get("src/test/resources/test-image.small.tar")
        val repository = Repository("python")
        val tag = Tag("test")

        client.exists(repository, tag) shouldBe false

        shouldNotThrowAny {
            client.upload(repository, tag, data.inputStream())
        }

        client.exists(repository, tag) shouldBe true
    }

    @Test
    fun testUploadLarge() {
        val data = Paths.get("src/test/resources/test-image-large.tar")
        val repository = Repository("python")
        val tag = Tag("test")

        client.exists(repository, tag) shouldBe false

        shouldNotThrowAny {
            client.upload(repository, tag, data.inputStream())
        }

        client.exists(repository, tag) shouldBe true
    }

    @Test
    fun test() {
        val localImage = "hello-world:linux"
        val targetImage = "${registry.address}/changeIt/myfirstimage:latest"
        registry.execInContainer("pull hello-world:linux")
        registry.execInContainer("docker tag $localImage $targetImage")
        registry.execInContainer("docker push $targetImage")

        client.exists(Repository("hello-world"), Tag("linux")).shouldBeTrue()
    }

    companion object {
        private val credentials = RegistryCredentials("changeIt", "changeIt")
        private val registry = TestRegistry().apply { start() }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            registry.stop()
        }
    }
}
