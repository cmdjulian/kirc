package de.cmdjulian.kirc.impl.delegate

import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.image.Tag
import de.cmdjulian.kirc.impl.KircApiError
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.ManifestJsonEntry
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.manifest.LayerReference
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import de.cmdjulian.kirc.utils.createSafePath
import de.cmdjulian.kirc.utils.toKotlinPath
import io.github.oshai.kotlinlogging.KotlinLogging
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
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val downloaderLogger = KotlinLogging.logger {}

internal class ImageDownloader(private val client: SuspendingContainerImageRegistryClient, private val tmpPath: Path) {

    suspend fun download(repository: Repository, reference: Reference, destination: Sink) {
        destination.use { sink ->
            try {
                val manifest = client.manifest(repository, reference)
                TarArchiveOutputStream(sink.asOutputStream()).use { tarStream ->
                    when (manifest) {
                        is ManifestSingle -> downloadSingleImage(repository, reference, manifest, tarStream)
                        is ManifestList -> downloadListImage(repository, reference, manifest, tarStream)
                    }
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    suspend fun download(repository: Repository, reference: Reference): Source {
        val tempDataPath = createSafePath(tmpPath, repository, reference, ".tar")
        val sink = withContext(Dispatchers.IO) {
            SystemFileSystem.sink(tempDataPath.toKotlinPath()).buffered()
        }

        download(repository, reference, sink)

        return withContext(Dispatchers.IO) { SystemFileSystem.source(tempDataPath.toKotlinPath()).buffered() }
    }

    private suspend fun downloadListImage(
        repository: Repository,
        reference: Reference,
        index: ManifestList,
        tarStream: TarArchiveOutputStream,
    ) {
        // Manifest digests -> ManifestSingle
        val manifests = index.manifests.associate { manifest ->
            manifest.digest to (client.manifest(repository, manifest.digest) as ManifestSingle)
        }
        val manifestSizes = index.manifests.associate { it.digest to it.size }
        val manifestConfigBlobs =
            manifests.mapValues { (_, manifest) -> client.blobStream(repository, manifest.config.digest) }
        val manifestLayers = manifests.mapValues { (_, manifest) -> manifest.layers }
        val digest = (reference as? Digest) ?: client.manifestDigest(repository, reference)
        val tags = client.tags(repository, digest)

        val manifestJson = createManifestJson(
            repository,
            tags,
            manifestLayers.mapValues { (_, values) -> values },
            manifests.mapValues { (_, manifest) -> manifest.config.digest },
        )
        val repositories = createRepositories(repository, *manifests.keys.toTypedArray())

        // Write meta entries
        tarStream.writeJsonEntry("index.json", index)
        tarStream.writeJsonEntry("manifest.json", manifestJson)
        tarStream.writeJsonEntry("repositories.json", repositories)
        tarStream.writeJsonEntry("oci-layout", OciLayout())

        // Write manifest blobs (their own digests) sequentially – small sized
        manifestSizes.forEach { (manifestDigest, size) ->
            val manifestStream = client.manifestStream(repository, manifestDigest)
            tarStream.writeSourceEntry("blobs/sha256/${manifestDigest.hash}", manifestStream.source, size)
        }

        // Write config blobs – small sized
        manifestConfigBlobs.forEach { (manifestDigest, configBlob) ->
            val manifest = manifests[manifestDigest] ?: throw KircException.InvalidState(
                "Could not resolve config digest for manifest '$manifestDigest' during download",
            )
            tarStream.writeSourceEntry(
                "blobs/sha256/${manifest.config.digest.hash}",
                configBlob,
                manifest.config.size,
            )
        }

        // Layers: deduplicate across architectures (same layer can be reused). Only write once per digest.
        val processedDigests = ConcurrentHashMap.newKeySet<Digest>()
        manifestLayers.values.forEach { layers ->
            layers.forEach { layer ->
                if (!processedDigests.add(layer.digest)) return@forEach
                tarStream.writeSourceEntry(
                    "blobs/sha256/${layer.digest.hash}",
                    client.blobStream(repository, layer.digest),
                    layer.size,
                )
            }
        }
    }

    private suspend fun downloadSingleImage(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle,
        tarStream: TarArchiveOutputStream,
    ): Unit = coroutineScope {
        val config = client.config(repository, reference)
        val configBlob = client.blobStream(repository, manifest.config.digest)
        val digest = (reference as? Digest) ?: client.manifestDigest(repository, reference)
        val manifestStream = client.manifestStream(repository, digest)

        // limit concurrent pull of layers to three at a time, like Docker does it
        val layerBlobs = with(Semaphore(3)) {
            manifest.layers.asSequence().associateWith { layer ->
                async {
                    withPermit { client.blobStream(repository, layer.digest) }
                }
            }
        }

        val indexManifest = ManifestListEntry(
            mediaType = OciManifestListV1.MediaType,
            digest = digest,
            size = manifestStream.size,
            platform = ManifestListEntry.Platform(config.os, config.architecture, emptyList()),
        ).let {
            OciManifestListV1(2, OciManifestListV1.MediaType, listOf(it), mapOf())
        }

        val manifestJson = createManifestJson(
            repository,
            client.tags(repository, digest),
            mapOf(digest to manifest.layers),
            mapOf(digest to manifest.config.digest),
        )
        val repositories = createRepositories(repository, digest)

        // Write meta entries
        tarStream.writeJsonEntry("index.json", indexManifest)
        tarStream.writeJsonEntry("manifest.json", manifestJson)
        tarStream.writeJsonEntry("repositories.json", repositories)
        tarStream.writeJsonEntry("oci-layout", OciLayout())

        // Write manifest blob, config blob
        tarStream.writeSourceEntry("blobs/sha256/${digest.hash}", manifestStream.source, manifestStream.size)
        tarStream.writeSourceEntry("blobs/sha256/${manifest.config.digest.hash}", configBlob, manifest.config.size)

        // Write layer blobs
        layerBlobs.forEach { (layer, blobSource) ->
            tarStream.writeSourceEntry("blobs/sha256/${layer.digest.hash}", blobSource.await(), layer.size)
        }
    }

    // ---- Tar writing helpers ----

    private suspend fun TarArchiveOutputStream.writeJsonEntry(name: String, data: Any) {
        val entryBytes = JsonMapper.writeValueAsBytes(data)
        val entry = TarArchiveEntry(name).apply { size = entryBytes.size.toLong() }
        withContext(Dispatchers.IO) {
            putArchiveEntry(entry)
            write(entryBytes)
            closeArchiveEntry()
            // TarArchiveOutputStream flush is implicit on underlying stream via write
        }
    }

    private suspend fun TarArchiveOutputStream.writeSourceEntry(name: String, data: Source, size: Long) {
        val entry = TarArchiveEntry(name).apply { this.size = size }
        withContext(Dispatchers.IO) {
            putArchiveEntry(entry)
            data.transferTo(asSink())
            closeArchiveEntry()
        }
    }

    private suspend fun createRepositories(repository: Repository, vararg digests: Digest): Repositories {
        val tagDigests = buildMap {
            for (digest in digests) {
                for (tag in client.tags(repository, digest)) {
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
        val configDigest = manifestConfig[manifestDigest]?.hash ?: throw KircException.InvalidState(
            "Could not resolve manifest config for manifest digest '$manifestDigest' during download",
        )

        ManifestJsonEntry(
            config = "blobs/sha256/$configDigest",
            repoTags = tags.map { tag -> "$repository${tag.asImagePart()}" },
            layers = layerPaths,
            layerSources = layerSources,
        )
    }.toTypedArray()

    private fun handleError(e: Exception): Nothing {
        downloaderLogger.error(e) { "Error downloading image from registry" }
        when (e) {
            is KircException, is KircApiError -> throw e
            else -> throw KircException.UnexpectedError(
                "Could not download image from registry: ${e.cause}",
                e,
            )
        }
    }
}
