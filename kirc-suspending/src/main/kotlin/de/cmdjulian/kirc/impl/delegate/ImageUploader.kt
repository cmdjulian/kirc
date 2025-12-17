package de.cmdjulian.kirc.impl.delegate

import de.cmdjulian.kirc.client.SuspendingContainerImageRegistryClient
import de.cmdjulian.kirc.client.UploadMode
import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.exception.RegistryException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.impl.serialization.deserialize
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
import de.cmdjulian.kirc.utils.createSafePath
import de.cmdjulian.kirc.utils.toKotlinPath
import io.github.oshai.kotlinlogging.KotlinLogging
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
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

internal class ImageUploader(private val client: SuspendingContainerImageRegistryClient, private val tmpPath: Path) {
    suspend fun upload(repository: Repository, reference: Reference, tar: Source, mode: UploadMode): Digest =
        coroutineScope {
            // store data temporarily (sanitize name for cross-platform safety)
            val tempDirectory = createSafePath(tmpPath, repository, reference)
            withContext(Dispatchers.IO) {
                SystemFileSystem.createDirectories(tempDirectory.toKotlinPath())
            }

            try {
                // read from tar and deserialize (can throw; ensure cleanup in finally)
                val uploadContainerImage = readFromTar(tar, tempDirectory)

                // upload architecture images
                for ((manifest, manifestDigest, blobs) in uploadContainerImage.images) {
                    // upload blobs in parallel, but only up to three at once to mimic how Docker does it
                    coroutineScope {
                        val semaphore = Semaphore(3)

                        for (blob in blobs.distinctBy(UploadBlobPath::digest)) {
                            launch {
                                semaphore.withPermit {
                                    uploadBlob(repository, blob, mode)
                                }
                            }
                        }
                    }
                    client.uploadManifest(repository, manifestDigest, manifest)
                }
                // upload index (manifest list) last
                client.uploadManifest(repository, reference, uploadContainerImage.index)
            } catch (e: RuntimeException) {
                handleError(e)
            } finally {
                // clear up temporary blob files
                runCatching { cleanupTempDirectory(tempDirectory) }
                    .onFailure { t ->
                        logger.info(t) { "Failed to fully cleanup temp directory: ${tempDirectory.pathString}" }
                    }
            }
        }

    private suspend fun uploadBlob(repository: Repository, blob: UploadBlobPath, mode: UploadMode) {
        if (!client.existsBlob(repository, blob.digest)) {
            val session = client.initiateBlobUpload(repository)

            when (mode) {
                UploadMode.Stream -> client.uploadBlobStream(session, blob.digest, blob.path, blob.size)

                is UploadMode.Chunks -> {
                    val endSession = client.uploadBlobChunks(session, blob.path, mode.chunkSize)
                    // chunked upload requires a final request
                    client.finishBlobUpload(endSession, blob.digest)
                }
            }
        }
    }

    private fun handleError(e: Exception): Nothing {
        val sanitizedError = when (e) {
            is KircException, is RegistryException -> e
            else -> KircException.UnexpectedError(
                "Unexpected error, could not upload image to registry: ${e.cause}",
                e,
            )
        }
        logger.error(sanitizedError) { "Error uploading image to registry" }
        throw sanitizedError
    }

    private suspend fun cleanupTempDirectory(tempDirectory: Path) {
        withContext(Dispatchers.IO) {
            // current implementation stores blobs flatly; still, iterate and delete defensively
            SystemFileSystem.list(tempDirectory.toKotlinPath()).forEach(SystemFileSystem::delete)
            SystemFileSystem.delete(tempDirectory.toKotlinPath())
        }
    }

    // PARSE INPUT TAR

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
                indexFile == null -> throw KircException.CorruptArchiveError(
                    "index should be present inside provided docker image",
                )

                ociLayoutFile == null -> throw KircException.CorruptArchiveError(
                    "'oci-layout' file should be present inside the provided docker image",
                )
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
                    val blobPath = blobPaths[layer.digest] ?: throw KircException.CorruptArchiveError(
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
        return runCatching {
            JsonMapper.deserialize<Manifest>(manifestStream)
        }.getOrElse {
            throw KircException.CorruptArchiveError("Could not deserialize manifest (digest=$manifestDigest)", it)
        }
    }
}
