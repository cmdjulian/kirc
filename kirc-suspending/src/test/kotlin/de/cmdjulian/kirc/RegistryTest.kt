package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.client.SuspendingContainerImageClientFactory
import io.kotest.matchers.collections.shouldBeEmpty
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import java.net.URI

internal class RegistryTest {
    private val client =
        SuspendingContainerImageClientFactory.create(registry.address.let(URI::create), credentials)

    @Test
    fun testConnection() = runTest {
        client.testConnection()
        client.repositories().shouldBeEmpty()
    }

    @Test
    fun test() {
        //val localImage = "hello-world:linux"
        //val targetImage = "${registry.address}/changeIt/myfirstimage:latest"
        //registry.execInContainer("pull hello-world:linux")
        //registry.execInContainer("docker tag $localImage $targetImage")
        //registry.execInContainer("docker push $targetImage")

        //client.exists(Repository("hello-world"), Tag("linux")).shouldBeTrue()
    }

    companion object {
        private val credentials = RegistryCredentials("changeIt", "changeIt")
        private val registry = TestRegistry().also(GenericContainer<*>::start)

        @AfterAll
        @JvmStatic
        fun tearDown() {
            registry.stop()
        }
    }
}
