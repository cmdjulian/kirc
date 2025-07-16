package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.BlockingContainerImageClientFactory
import de.cmdjulian.kirc.client.BlockingContainerImageRegistryClient
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.nio.file.Paths
import kotlin.io.path.inputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class BlockingRegistryTest {
    private lateinit var client: BlockingContainerImageRegistryClient
    private lateinit var registry: RegistryTestContainer

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
    fun testConnection() {
        shouldNotThrowAny {
            client.testConnection()
        }
        client.repositories().shouldBeEmpty()
    }

    @Test
    fun testRepositories() {
        client.repositories().shouldBeEmpty()
        DockerRegistryCliHelper.pushImage(registry.addressName, "test", "hello-world:latest")
        client.repositories().shouldNotBeEmpty().shouldHaveSize(1)
    }

    @Test
    fun testRepositories2() {
        client.repositories().shouldBeEmpty()
        DockerRegistryCliHelper.pushImage(registry.addressName, "test", "hello-world:latest")
        client.repositories().shouldNotBeEmpty().shouldHaveSize(1)
    }

    @Test
    fun testUploadLarge() {
        val data = Paths.get(HELLO_WORLD_IMAGE)
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
        val targetImage = "${registry.addressHttp}/changeIt/myfirstimage:latest"
        registry.execInContainer("pull hello-world:linux")
        registry.execInContainer("docker tag $localImage $targetImage")
        registry.execInContainer("docker push $targetImage")

        client.exists(Repository("hello-world"), Tag("linux")).shouldBeTrue()
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
