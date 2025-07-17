package de.cmdjulian.kirc.impl.registry

import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.JsonMapper
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.ManifestJsonEntry
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asOutputStream
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

internal interface DownloadImageRegistryImpl : SuspendingContainerImageRegistryClient {

    override suspend fun download(repository: Repository, reference: Reference): Sink =
        Path(
            SystemTemporaryDirectory,
            "${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)}--$repository--$reference.tar",
        ).let(SystemFileSystem::sink).buffered().use { sink ->

            when (val manifest = manifest(repository, reference)) {
                is ManifestSingle -> downloadSingleImage(repository, reference, manifest, sink)

                is ManifestList -> downloadListImage(repository, reference, manifest, sink)
            }

            sink
        }

    private suspend fun downloadListImage(
        repository: Repository,
        reference: Reference,
        index: ManifestList,
        sink: Sink,
    ): Unit = coroutineScope {
        val manifests = index.manifests.associate { manifest ->
            manifest.digest to async(Dispatchers.IO) {
                manifest(repository, manifest.digest) as ManifestSingle
            }
        }
        val manifestConfig = manifests.mapValues { (_, manifest) -> async { config(repository, manifest.await()) } }
        val manifestLayers = manifests.mapValues { (_, manifest) -> async { manifest.await().layers } }
        val tag = (reference as? Tag) ?: Tag.LATEST // todo extract tag from "org.opencontainers.image.ref.name"

        val manifestJson = async {
            createManifestJson(
                repository,
                reference,
                manifestLayers.mapValues { (_, values) -> values.await() },
                manifests.mapValues { (_, manifest) -> manifest.await().config.digest },
            )
        }
        val repositories = async {
            createRepositories(repository, tag, *manifests.keys.toTypedArray())
        }

        async(Dispatchers.IO) {
            sink.writeTarEntry("index.json", index)
            sink.writeTarEntry("manifest.json", manifestJson.await())
            sink.writeTarEntry("repositories.json", repositories.await())
            sink.writeTarEntry("oci-layout", OciLayout())

            manifests.forEach { (digest, manifest) ->
                sink.writeTarEntry("blobs/sha256/${digest.hash}", manifest.await())
            }

            manifestConfig.map { (manifestDigest, config) ->
                val configDigest = manifests[manifestDigest]!!.await().config.digest
                sink.writeTarEntry("blobs/sha256/${configDigest.hash}", config.await())
            }

            manifestLayers.forEach { (_, layers) ->
                layers.await().forEach { layer ->
                    sink.writeTarEntryFromSource(
                        "blobs/sha256/${layer.digest.hash}",
                        blobStream(repository, layer.digest),
                        layer.size,
                    )
                }
            }
        }.await()
    }

    private suspend fun downloadSingleImage(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle,
        sink: Sink,
    ): Unit = coroutineScope {
        val config = config(repository, reference)
        val digest = (reference as? Digest) ?: manifestDigest(repository, reference)
        val tag = (reference as? Tag) ?: Tag.LATEST

        // limit concurrent pull of layers to three at a time, like Docker does it
        val layerBlobs = with(Semaphore(3)) {
            manifest.layers.asSequence().associateWith { layer ->
                async(Dispatchers.IO) {
                    withPermit { blobStream(repository, layer.digest) }
                }
            }
        }

        val indexManifest = ManifestListEntry(
            mediaType = OciManifestListV1.MediaType,
            digest = digest,
            size = manifest.layers.sumOf(LayerReference::size),
            platform = ManifestListEntry.Platform(config.os, config.architecture, emptyList()),
        ).let {
            OciManifestListV1(2, OciManifestListV1.MediaType, listOf(it), mapOf())
        }

        val manifestJson = createManifestJson(
            repository,
            reference,
            mapOf(digest to manifest.layers),
            mapOf(digest to manifest.config.digest),
        )
        val repositories = createRepositories(repository, tag, digest)


        async(Dispatchers.IO) {
            sink.writeTarEntry("index.json", indexManifest)
            sink.writeTarEntry("manifest.json", manifestJson)
            sink.writeTarEntry("repositories.json", repositories)
            sink.writeTarEntry("oci-layout", OciLayout())

            layerBlobs.forEach { (layer, blobSource) ->
                sink.writeTarEntryFromSource("blobs/sha256/${layer.digest.hash}", blobSource.await(), layer.size)
            }
        }
    }

    private fun Sink.writeTarEntry(name: String, data: Any) {
        if (data is Deferred<*>) error("Data to write shouldn't be deferred")

        TarArchiveOutputStream(asOutputStream()).also { tarStream ->
            val indexEntry = TarArchiveEntry(name)
            val indexBytes = JsonMapper.writeValueAsBytes(data)
            indexEntry.size = indexBytes.size.toLong()
            tarStream.putArchiveEntry(indexEntry)
            tarStream.write(indexBytes)
            tarStream.closeArchiveEntry()
        }
    }

    private fun Sink.writeTarEntryFromSource(name: String, data: Source, size: Long) {
        TarArchiveOutputStream(asOutputStream()).also { tarStream ->
            val indexEntry = TarArchiveEntry(name)
            indexEntry.size = size
            tarStream.putArchiveEntry(indexEntry)
            data.transferTo(tarStream.asSink())
            tarStream.closeArchiveEntry()
        }
    }

    private fun createRepositories(repository: Repository, tag: Tag, vararg digests: Digest): Repositories {
        val tagDigest = digests.associate { digest ->
            tag to digest.hash
        }

        return mapOf(repository to tagDigest)
    }

    private fun createManifestJson(
        repository: Repository,
        reference: Reference,
        manifestLayers: Map<Digest, List<LayerReference>>, // manifestDigest -> layers
        manifestConfig: Map<Digest, Digest>, // manifestDigest -> configDigest
    ): ManifestJson = manifestLayers.map { (manifestDigest, layers) ->
        val layerPath = layers.map { layer -> "blobs/sha256/${layer.digest.hash}" }
        val layerSources = layers.associateBy(LayerReference::digest)

        // todo
        val tag = when (reference) {
            is Digest -> Tag.LATEST
            is Tag -> reference
        }

        ManifestJsonEntry(
            config = "blobs/sha256/${manifestConfig[manifestDigest]!!.hash}",
            repoTags = listOf("$repository${tag.asImagePart()}"),
            layers = layerPath,
            layerSources = layerSources,
        )
    }
}