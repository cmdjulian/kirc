package de.cmdjulian.kirc.impl

import com.adelean.inject.resources.junit.jupiter.GivenTextResource
import com.adelean.inject.resources.junit.jupiter.TestWithResources
import de.cmdjulian.kirc.spec.Architecture
import de.cmdjulian.kirc.spec.OS
import de.cmdjulian.kirc.spec.image.DockerImageConfigV1
import de.cmdjulian.kirc.spec.image.History
import de.cmdjulian.kirc.spec.image.OciImageConfigV1
import de.cmdjulian.kirc.spec.image.RootFs
import de.cmdjulian.kirc.unmarshal
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

@TestWithResources
internal class ImageConfigDeserializationTest {
    @Test
    fun ociImageConfigV1(@GivenTextResource("OciImageConfigV1.json") json: String) {
        assertSoftly(json.unmarshal<OciImageConfigV1>()) {
            created shouldBe OffsetDateTime.parse("2015-10-31T22:22:56.015925234Z")
            author shouldBe "Alyssa P. Hacker <alyspdev@example.com>"
            architecture shouldBe Architecture.AMD64
            os shouldBe OS.LINUX

            assertSoftly(config.shouldNotBeNull()) {
                hostname shouldBe null
                domainname shouldBe null
                user shouldBe "alice"
                exposedPorts shouldContainExactly mapOf("8080/tcp" to emptyMap<String, Any>())
                attachStdin shouldBe null
                attachStdout shouldBe null
                attachStderr shouldBe null
                tty shouldBe null
                openStdin shouldBe null
                stdinOnce shouldBe null
                env shouldContainExactly listOf(
                    "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                    "FOO=oci_is_a",
                    "BAR=well_written_spec",
                )
                entrypoint shouldContainExactly listOf("/bin/my-app-binary")
                cmd shouldContainExactly listOf("--foreground", "--config", "/etc/my-app.d/default.cfg")
                image shouldBe null
                volumes shouldContainExactly mapOf(
                    "/var/job-result-data" to emptyMap<String, Any>(),
                    "/var/log/my-app-logs" to emptyMap<String, Any>(),
                )
                workingDir shouldBe "/home/alice"
                labels shouldContainExactly mapOf(
                    "com.example.project.git.url" to "https://example.com/project.git",
                    "com.example.project.git.commit" to "45a939b2999782a3f005621a8d0f29aa387e1d6b",
                )
                stopSignal shouldBe null
                argsEscaped shouldBe null
                memory shouldBe null
                memorySwap shouldBe null
                cpuShares shouldBe null
                healthcheck shouldBe null
            }
            rootfs shouldBe RootFs(
                "layers",
                listOf(
                    "sha256:c6f988f4874bb0add23a778f753c65efe992244e148a1d2ec2a8b664fb66bbd1",
                    "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef",
                ),
            )
            history shouldContainExactly listOf(
                History(
                    OffsetDateTime.parse("2015-10-31T22:22:54.690851953Z"),
                    null,
                    "/bin/sh -c #(nop) ADD file:a3bc1e842b69636f9df5256c49c5374fb4eef1e281fe3f282c65fb853ee171c5 in /",
                    null,
                    null,
                ),
                History(
                    OffsetDateTime.parse("2015-10-31T22:22:55.613815829Z"),
                    null,
                    "/bin/sh -c #(nop) CMD [\"sh\"]",
                    true,
                    null,
                ),
                History(
                    OffsetDateTime.parse("2015-10-31T22:22:56.329850019Z"),
                    null,
                    "/bin/sh -c apk add curl",
                    null,
                    null,
                ),
            )
        }
    }

    @Test
    fun dockerImageConfigV1(@GivenTextResource("DockerImageConfigV1.json") json: String) {
        assertSoftly(json.unmarshal<DockerImageConfigV1>()) {
            id shouldBe null
            parent shouldBe null
            onBuild.shouldBeEmpty()
            author shouldBe null
            checksum shouldBe null
            size shouldBe null
            architecture shouldBe Architecture.AMD64
            assertSoftly(config.shouldNotBeNull()) {
                hostname shouldBe ""
                domainname shouldBe ""
                user shouldBe ""
                attachStdin shouldBe false
                attachStdout shouldBe false
                attachStderr shouldBe false
                tty shouldBe false
                openStdin shouldBe false
                stdinOnce shouldBe false
                env shouldContainExactly listOf("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                cmd shouldContainExactly listOf("/bin/sh")
                image shouldBe "sha256:ba2beca50019d79fb31b12c08f3786c5a0621017a3e95a72f2f8b832f894a427"
                volumes.shouldBeEmpty()
                workingDir shouldBe ""
                entrypoint.shouldBeEmpty()
                onBuild.shouldBeEmpty()
                labels.shouldBeEmpty()
                exposedPorts.shouldBeEmpty()
                stopSignal shouldBe null
                argsEscaped shouldBe null
                memory shouldBe null
                memorySwap shouldBe null
                cpuShares shouldBe null
                healthcheck shouldBe null
            }
            container shouldBe "4ad3f57821a165b2174de22a9710123f0d35e5884dca772295c6ebe85f74fe57"
            assertSoftly(containerConfig.shouldNotBeNull()) {
                hostname shouldBe "4ad3f57821a1"
                domainname shouldBe ""
                user shouldBe ""
                attachStdin shouldBe false
                attachStdout shouldBe false
                attachStderr shouldBe false
                tty shouldBe false
                openStdin shouldBe false
                stdinOnce shouldBe false
                env shouldContainExactly listOf("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                cmd shouldContainExactly listOf("/bin/sh", "-c", "#(nop) ", "CMD [\"/bin/sh\"]")
                image shouldBe "sha256:ba2beca50019d79fb31b12c08f3786c5a0621017a3e95a72f2f8b832f894a427"
                volumes.shouldBeEmpty()
                workingDir shouldBe ""
                entrypoint.shouldBeEmpty()
                onBuild.shouldBeEmpty()
                labels.shouldBeEmpty()
                exposedPorts.shouldBeEmpty()
                stopSignal shouldBe null
                argsEscaped shouldBe null
                memory shouldBe null
                memorySwap shouldBe null
                cpuShares shouldBe null
                healthcheck shouldBe null
            }
            created shouldBe OffsetDateTime.parse("2023-02-11T04:46:42.558343068Z")
            dockerVersion shouldBe "20.10.12"
            history shouldContainExactly listOf(
                History(
                    OffsetDateTime.parse("2023-02-11T04:46:42.449083344Z"),
                    null,
                    "/bin/sh -c #(nop) ADD file:40887ab7c06977737e63c215c9bd297c0c74de8d12d16ebdf1c3d40ac392f62d in / ",
                    null,
                    null,
                ),
                History(
                    OffsetDateTime.parse("2023-02-11T04:46:42.558343068Z"),
                    null,
                    "/bin/sh -c #(nop)  CMD [\"/bin/sh\"]",
                    true,
                    null,
                ),
            )
            os shouldBe OS.LINUX
            rootfs shouldBe RootFs(
                "layers",
                listOf("sha256:7cd52847ad775a5ddc4b58326cf884beee34544296402c6292ed76474c686d39"),
            )
            author shouldBe null
        }
    }
}
