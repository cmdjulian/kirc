package de.cmdjulian.distribution.spec.image

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.OffsetDateTime

/*
 * {
 *    "architecture":"amd64",
 *    "config":{
 *       "Hostname":"",
 *       "Domainname":"",
 *       "User":"",
 *       "AttachStdin":false,
 *       "AttachStdout":false,
 *       "AttachStderr":false,
 *       "Tty":false,
 *       "OpenStdin":false,
 *       "StdinOnce":false,
 *       "Env":[
 *          "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
 *       ],
 *       "Cmd":[
 *          "/bin/sh"
 *       ],
 *       "Image":"sha256:ba2beca50019d79fb31b12c08f3786c5a0621017a3e95a72f2f8b832f894a427",
 *       "Volumes":null,
 *       "WorkingDir":"",
 *       "Entrypoint":null,
 *       "OnBuild":null,
 *       "Labels":null
 *    },
 *    "container":"4ad3f57821a165b2174de22a9710123f0d35e5884dca772295c6ebe85f74fe57",
 *    "container_config":{
 *       "Hostname":"4ad3f57821a1",
 *       "Domainname":"",
 *       "User":"",
 *       "AttachStdin":false,
 *       "AttachStdout":false,
 *       "AttachStderr":false,
 *       "Tty":false,
 *       "OpenStdin":false,
 *       "StdinOnce":false,
 *       "Env":[
 *          "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
 *       ],
 *       "Cmd":[
 *          "/bin/sh",
 *          "-c",
 *          "#(nop) ",
 *          "CMD [\"/bin/sh\"]"
 *       ],
 *       "Image":"sha256:ba2beca50019d79fb31b12c08f3786c5a0621017a3e95a72f2f8b832f894a427",
 *       "Volumes":null,
 *       "WorkingDir":"",
 *       "Entrypoint":null,
 *       "OnBuild":null,
 *       "Labels":{
 *
 *       }
 *    },
 *    "created":"2023-02-11T04:46:42.558343068Z",
 *    "docker_version":"20.10.12",
 *    "history":[
 *       {
 *          "created":"2023-02-11T04:46:42.449083344Z",
 *          "created_by":"/bin/sh -c #(nop) ADD file:40887ab7c06977737e63c215c9bd297c0c74de8d12d16ebdf1c3d40ac392f62d in / "
 *       },
 *       {
 *          "created":"2023-02-11T04:46:42.558343068Z",
 *          "created_by":"/bin/sh -c #(nop)  CMD [\"/bin/sh\"]",
 *          "empty_layer":true
 *       }
 *    ],
 *    "os":"linux",
 *    "rootfs":{
 *       "type":"layers",
 *       "diff_ids":[
 *          "sha256:7cd52847ad775a5ddc4b58326cf884beee34544296402c6292ed76474c686d39"
 *       ]
 *    }
 * }
 */
// https://github.com/moby/moby/blob/master/image/spec/v1.2.md
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DockerImageConfigV1(
    val id: String?,
    val parent: String?,
    override val created: OffsetDateTime?,
    val dockerVersion: String?,
    val onBuild: List<String>?,
    override val author: String?,
    override val architecture: String,
    override val os: String,
    val checksum: String?,
    @JsonProperty("Size") val size: UInt,
    override val config: ImageConfig.Config?,
    val containerConfig: ImageConfig?,
    override val rootfs: RootFs,
    override val history: List<History>?,
) : ImageConfig {
    companion object {
        const val MediaType = "application/vnd.docker.container.image.v1+json"
    }
}
