package de.cmdjulian.kirc.impl.delegate

import de.cmdjulian.kirc.KircUploadException
import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.jacksonDeserializer
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.UploadBlobPath
import de.cmdjulian.kirc.spec.UploadContainerImage
import de.cmdjulian.kirc.spec.UploadSingleImage
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import de.cmdjulian.kirc.utils.toKotlinPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.io.path.pathString

internal class ImageUploader(private val client: SuspendingContainerImageRegistryClient, private val tmpPath: Path) {

    suspend fun upload(repository: Repository, reference: Reference, tar: Source): Digest = coroutineScope {
        // store data temporarily
        val tempDirectory = Path.of(
            tmpPath.pathString,
            "${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)}--$repository--$reference",
        )
        withContext(Dispatchers.IO) {
            SystemFileSystem.createDirectories(tempDirectory.toKotlinPath())
        }

        // read from temp file and deserialize
        val uploadContainerImage = readFromTar(tar, tempDirectory)

        // upload architecture images
        try {
            for ((manifest, manifestDigest, blobs) in uploadContainerImage.images) {
                // upload blobs in parallel, but only up to three at once to mimic how Docker does it
                coroutineScope {
                    val semaphore = Semaphore(3)

                    for (blob in blobs.distinctBy(UploadBlobPath::digest)) {
                        launch {
                            semaphore.withPermit {
                                if (!client.existsBlob(repository, blob.digest)) {
                                    val session = client.initiateBlobUpload(repository)

                                    val endSession = client.uploadBlobChunks(session, blob.path)

                                    client.finishBlobUpload(endSession, blob.digest)
                                }
                            }
                        }
                    }
                }

                client.uploadManifest(repository, manifestDigest, manifest)
            }
        } finally {
            // clear up temporary blob files
            withContext(Dispatchers.IO) {
                SystemFileSystem.list(tempDirectory.toKotlinPath()).forEach(SystemFileSystem::delete)
                SystemFileSystem.delete(tempDirectory.toKotlinPath())
            }
        }

        // upload index
        client.uploadManifest(repository, reference, uploadContainerImage.index)
    }

    private suspend fun readFromTar(tarSource: Source, tempDirectory: Path) = tarSource.use { source ->
        TarArchiveInputStream(source.asInputStream()).use { stream ->
            val blobs = mutableMapOf<Digest, Path>()
            var indexFile: ManifestList? = null
            var repositoriesFile: Repositories? = null
            var manifestJsonFile: ManifestJson? = null
            var ociLayoutFile: OciLayout? = null

            generateSequence(stream::getNextEntry).forEach { entry ->
                when {
                    entry.isDirectory -> stream.skip(entry.size)

                    entry.name.startsWith("blobs/sha256/") -> {
                        val digest = Digest.of("sha256:" + entry.name.removePrefix("blobs/sha256/"))
                        blobs[digest] = stream.processBlobEntry(entry, tempDirectory)
                    }

                    "index.json" == entry.name -> indexFile = stream.deserializeEntry<ManifestList>(entry)

                    "repositories" == entry.name -> repositoriesFile = stream.deserializeEntry<Repositories>(entry)

                    "manifest.json" == entry.name -> manifestJsonFile = stream.deserializeEntry<ManifestJson>(entry)

                    "oci-layout" == entry.name -> ociLayoutFile = stream.deserializeEntry<OciLayout>(entry)

                    else -> stream.skip(entry.size)
                }
            }

            when {
                indexFile == null -> throw KircUploadException("index should be present inside provided docker image")

                ociLayoutFile == null ->
                    throw KircUploadException("'oci-layout' file should be present inside the provided docker image")
            }

            val (processedIndex, resolvedManifests) = resolveManifestsAndBlobs(indexFile, blobs)
            UploadContainerImage(
                index = processedIndex,
                images = resolvedManifests,
                manifest = manifestJsonFile,
                repositories = repositoriesFile,
                layout = ociLayoutFile,
            )
        }
    }

    /**
     * Recursively resolves all Manifests from blobs and associates manifest with its layer blobs and config blobs.
     * Removes manifest attachments from manifests in the process if their platform contains UNKNOWN.
     *
     * Returns the resolved images as well as the index provided, stripped from all attachments (attestations, cache, etc.).
     *
     * [index] - the provided [ManifestList] for which manifests should be resolved
     * [blobPaths] - a mapping of blob digests to their source path
     */
    private suspend fun resolveManifestsAndBlobs(
        index: ManifestList,
        blobPaths: Map<Digest, Path>,
    ): Pair<ManifestList, List<UploadSingleImage>> {
        val attachments = mutableListOf<Digest>()
        val manifestBlobs = buildList {
            for (manifestEntry in index.manifests) {
                val entryDigest = manifestEntry.digest
                // skip manifests with unknown platform (attestations, cache, etc.), they will be filtered out
                if (manifestEntry.platformIsUnknown()) {
                    attachments.add(entryDigest)
                    continue
                }
                // resolve manifests from index
                val manifestBlobPath = blobPaths[entryDigest] ?: continue
                val manifestBlob =
                    UploadBlobPath(entryDigest, manifestEntry.mediaType, manifestBlobPath, manifestEntry.size)
                addAll(resolveManifestsRecursively(entryDigest, blobPaths, manifestBlob))
            }
        }
        val manifests = index.manifests.filterNot { it.digest in attachments }
        val index = when (index) {
            is DockerManifestListV1 -> index.copy(manifests = manifests)
            is OciManifestListV1 -> index.copy(manifests = manifests)
        }
        return index to manifestBlobs
    }

    private suspend fun resolveManifestsRecursively(
        entryDigest: Digest,
        blobPaths: Map<Digest, Path>,
        manifestBlob: UploadBlobPath,
    ) = buildList {
        when (val manifest = resolveManifest(blobPaths, entryDigest)) {
            null -> Unit
            is ManifestList -> {
                // recursively resolve manifests
                val (processedManifestList, processedManifests) = resolveManifestsAndBlobs(manifest, blobPaths)
                addAll(processedManifests)
                // add the manifest list itself as an image too, so that it can be referenced elsewhere
                add(UploadSingleImage(processedManifestList, entryDigest, listOf(manifestBlob)))
            }

            is ManifestSingle -> {
                // associate manifests to their layer blobs and config blob
                val blobs = (manifest.layers + manifest.config).map { layer ->
                    val blobPath = blobPaths[layer.digest] ?: throw KircUploadException(
                        "Could not resolve blob for layer or " +
                            "config with digest '${layer.digest}' during upload",
                    )
                    UploadBlobPath(layer.digest, layer.mediaType, blobPath, layer.size)
                }
                add(UploadSingleImage(manifest, entryDigest, blobs + manifestBlob))
            }
        }
    }

    private suspend fun resolveManifest(blobs: Map<Digest, Path>, manifestDigest: Digest): Manifest? {
        val blobPath = blobs[manifestDigest] ?: return null

        val manifestStream = withContext(Dispatchers.IO) {
            SystemFileSystem.source(blobPath.toKotlinPath()).buffered().asInputStream()
        }
        return jacksonDeserializer<Manifest>().deserialize(manifestStream)
    }
}
