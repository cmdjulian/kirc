package de.cmdjulian.kirc.impl.registry

import de.cmdjulian.kirc.KircInternalError
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
import kotlinx.coroutines.withContext
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

    override suspend fun download(repository: Repository, reference: Reference): Sink {
        val tempDataPath = Path(
            SystemTemporaryDirectory,
            "${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)}--$repository--$reference.tar",
        )
        val sink = withContext(Dispatchers.IO) {
            SystemFileSystem.sink(tempDataPath).buffered()
        }

        sink.use { sink ->
            when (val manifest = manifest(repository, reference)) {
                is ManifestSingle -> downloadSingleImage(repository, reference, manifest, sink)
                is ManifestList -> downloadListImage(repository, reference, manifest, sink)
            }
        }

        return sink
    }

    private suspend fun downloadListImage(
        repository: Repository,
        reference: Reference,
        index: ManifestList,
        sink: Sink,
    ): Unit = coroutineScope {
        val manifests = index.manifests.associate { manifest ->
            manifest.digest to async { manifest(repository, manifest.digest) as ManifestSingle }
        }
        val manifestConfig = manifests.mapValues { (_, manifest) -> async { config(repository, manifest.await()) } }
        val manifestLayers = manifests.mapValues { (_, manifest) -> async { manifest.await().layers } }
        val digest = (reference as? Digest) ?: manifestDigest(repository, reference)
        val tags = tags(repository, digest)

        val manifestJson = async {
            createManifestJson(
                repository,
                tags,
                manifestLayers.mapValues { (_, values) -> values.await() },
                manifests.mapValues { (_, manifest) -> manifest.await().config.digest },
            )
        }
        val repositories = async {
            createRepositories(repository, *manifests.keys.toTypedArray())
        }

        // write data
        async {
            sink.writeTarEntry("index.json", index)
            sink.writeTarEntry("manifest.json", manifestJson)
            sink.writeTarEntry("repositories.json", repositories)
            sink.writeTarEntry("oci-layout", OciLayout())

            manifests.forEach { (digest, manifest) ->
                sink.writeTarEntry("blobs/sha256/${digest.hash}", manifest)
            }

            manifestConfig.map { (manifestDigest, config) ->
                val configDigest = manifests[manifestDigest]?.await()?.config?.digest ?: throw KircInternalError(
                    "Could not resolve config digest for manifest '$manifestDigest' during download",
                )
                sink.writeTarEntry("blobs/sha256/${configDigest.hash}", config)
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

        // limit concurrent pull of layers to three at a time, like Docker does it
        val layerBlobs = with(Semaphore(3)) {
            manifest.layers.asSequence().associateWith { layer ->
                async {
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
            tags(repository, digest),
            mapOf(digest to manifest.layers),
            mapOf(digest to manifest.config.digest),
        )
        val repositories = createRepositories(repository, digest)
        
        // write data
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

    //
    private suspend fun Sink.writeTarEntry(name: String, data: Deferred<Any>) = writeTarEntry(name, data.await())

    private suspend fun Sink.writeTarEntry(name: String, data: Any) {
        TarArchiveOutputStream(asOutputStream()).also { tarStream ->
            val indexEntry = TarArchiveEntry(name)
            val indexBytes = JsonMapper.writeValueAsBytes(data)
            indexEntry.size = indexBytes.size.toLong()
            withContext(Dispatchers.IO) {
                tarStream.putArchiveEntry(indexEntry)
                tarStream.write(indexBytes)
                tarStream.closeArchiveEntry()
            }
        }
    }

    private suspend fun Sink.writeTarEntryFromSource(name: String, data: Source, size: Long) {
        TarArchiveOutputStream(asOutputStream()).also { tarStream ->
            val indexEntry = TarArchiveEntry(name)
            indexEntry.size = size
            withContext(Dispatchers.IO) {
                tarStream.putArchiveEntry(indexEntry)
                data.transferTo(tarStream.asSink())
                tarStream.closeArchiveEntry()
            }
        }
    }

    private suspend fun createRepositories(repository: Repository, vararg digests: Digest): Repositories {
        val tagDigests = buildMap {
            for (digest in digests) {
                for (tag in tags(repository, digest)) {
                    put(tag, digest.hash)
                }
            }
        }

        return mapOf(repository to tagDigests)
    }

    private fun createManifestJson(
        repository: Repository,
        tags: List<Tag>,
        manifestLayers: Map<Digest, List<LayerReference>>, // manifestDigest -> layers
        manifestConfig: Map<Digest, Digest>, // manifestDigest -> configDigest
    ): ManifestJson = manifestLayers.map { (manifestDigest, layers) ->
        val layerPaths = layers.map { layer -> "blobs/sha256/${layer.digest.hash}" }
        val layerSources = layers.associateBy(LayerReference::digest)
        val configDigest = manifestConfig[manifestDigest]?.hash ?: throw KircInternalError(
            "Could not resolve manifest config for manifest digest '$manifestDigest' during download",
        )

        ManifestJsonEntry(
            config = "blobs/sha256/$configDigest",
            repoTags = tags.map { tag -> "$repository${tag.asImagePart()}" },
            layers = layerPaths,
            layerSources = layerSources,
        )
    }
}