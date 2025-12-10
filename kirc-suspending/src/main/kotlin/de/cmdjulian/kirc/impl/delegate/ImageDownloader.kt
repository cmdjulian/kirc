package de.cmdjulian.kirc.impl.delegate

import de.cmdjulian.kirc.KircDownloadException
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
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import de.cmdjulian.kirc.utils.toKotlinPath
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
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.io.path.pathString

internal class ImageDownloader(private val client: SuspendingContainerImageRegistryClient, private val tmpPath: Path) {

    suspend fun download(repository: Repository, reference: Reference, destination: Sink) {
        destination.use { sink ->
            when (val manifest = client.manifest(repository, reference)) {
                is ManifestSingle -> downloadSingleImage(repository, reference, manifest, sink)
                is ManifestList -> downloadListImage(repository, reference, manifest, sink)
            }
        }
    }

    suspend fun download(repository: Repository, reference: Reference): Source {
        val tempDataPath = Path.of(
            tmpPath.pathString,
            "${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)}--$repository--$reference.tar",
        )
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
        sink: Sink,
    ) {
        val manifests = resolveManifests(index, repository)
        val digest = (reference as? Digest) ?: client.manifestDigest(repository, reference)
        val tags = client.tags(repository, digest)

        val singleManifests = manifests.filterIsInstance<ResolvedManifest.Single>()
        val manifestJson = createManifestJson(
            repository,
            tags,
            singleManifests.associate { resolved -> resolved.digest to resolved.manifest.layers },
            singleManifests.associate { resolved -> resolved.digest to resolved.manifest.config.digest },
        )
        val repositories = createRepositories(repository, *manifests.map(ResolvedManifest::digest).toTypedArray())

        // write data
        sink.writeTarEntry("index.json", index)
        sink.writeTarEntry("manifest.json", manifestJson)
        sink.writeTarEntry("repositories.json", repositories)
        sink.writeTarEntry("oci-layout", OciLayout())

        for (resolved in manifests) {
            // write manifest blob
            val manifestStream = client.manifestStream(repository, resolved.digest)
            sink.writeTarEntryFromSource(
                "blobs/sha256/${resolved.digest.hash}",
                manifestStream.source,
                resolved.size,
            )

            if (resolved is ResolvedManifest.Single) {
                // write config blob
                val config = resolved.manifest.config
                sink.writeTarEntryFromSource("blobs/sha256/${config.digest.hash}", resolved.configStream, config.size)

                // write layer blobs
                for (layer in resolved.manifest.layers) {
                    sink.writeTarEntryFromSource(
                        "blobs/sha256/${layer.digest.hash}",
                        client.blobStream(repository, layer.digest),
                        layer.size,
                    )
                }
            }
        }
    }

    // recursively resolve all manifests from a manifest list
    private suspend fun resolveManifests(index: ManifestList, repository: Repository): List<ResolvedManifest> =
        buildList {
            for (manifestEntry in index.manifests) {
                // Some manifest attachments are not retrievable, skip those as they aren't currently supported in kirc
                if (manifestEntry.platformIsUnknown()) continue
                when (val manifest = client.manifest(repository, manifestEntry.digest)) {
                    is ManifestSingle -> {
                        val configStream = client.blobStream(repository, manifest.config.digest)
                        add(ResolvedManifest.Single(manifest, manifestEntry.digest, manifestEntry.size, configStream))
                    }

                    is ManifestList -> {
                        add(ResolvedManifest.List(manifest, manifestEntry.digest, manifestEntry.size))
                        addAll(resolveManifests(manifest, repository))
                    }
                }
            }
        }

    private suspend fun downloadSingleImage(
        repository: Repository,
        reference: Reference,
        manifest: ManifestSingle,
        sink: Sink,
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
            annotations = emptyMap(),
        ).let {
            OciManifestListV1(2, OciManifestListV1.MediaType, mutableListOf(it), mapOf())
        }

        val manifestJson = createManifestJson(
            repository,
            client.tags(repository, digest),
            mapOf(digest to manifest.layers),
            mapOf(digest to manifest.config.digest),
        )
        val repositories = createRepositories(repository, digest)

        // write data

        sink.writeTarEntry("index.json", indexManifest)
        sink.writeTarEntry("manifest.json", manifestJson)
        sink.writeTarEntry("repositories.json", repositories)
        sink.writeTarEntry("oci-layout", OciLayout())

        sink.writeTarEntryFromSource("blobs/sha256/${digest.hash}", manifestStream.source, manifestStream.size)
        sink.writeTarEntryFromSource("blobs/sha256/${manifest.config.digest.hash}", configBlob, manifest.config.size)
        layerBlobs.forEach { (layer, blobSource) ->
            sink.writeTarEntryFromSource("blobs/sha256/${layer.digest.hash}", blobSource.await(), layer.size)
        }
    }

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
        val configDigest = manifestConfig[manifestDigest]?.hash ?: throw KircDownloadException(
            "Could not resolve manifest config for manifest digest '$manifestDigest' during download",
        )

        ManifestJsonEntry(
            config = "blobs/sha256/$configDigest",
            repoTags = tags.map { tag -> "$repository${tag.asImagePart()}" },
            layers = layerPaths,
            layerSources = layerSources,
        )
    }.toTypedArray()

    private interface ResolvedManifest {
        val manifest: Manifest
        val digest: Digest
        val size: Long

        data class Single(
            override val manifest: ManifestSingle,
            override val digest: Digest,
            override val size: Long,
            val configStream: Source,
        ) : ResolvedManifest

        data class List(
            override val manifest: ManifestList,
            override val digest: Digest,
            override val size: Long,
        ) : ResolvedManifest
    }
}
