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
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.utils.toKotlinPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.pathString

internal class ImageUploader(private val client: SuspendingContainerImageRegistryClient, private val tmpPath: Path) {

    @OptIn(ExperimentalPathApi::class, ExperimentalCoroutinesApi::class)
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
        // upload max three layers in parallel at most
        val blobUploadDispatcher = Dispatchers.Default.limitedParallelism(3)
        try {
            for ((manifest, manifestDigest, blobs) in uploadContainerImage.images) {
                // upload blobs in parallel, but only up to three at once to mimic how Docker does it
                coroutineScope {
                    withContext(blobUploadDispatcher) {
                        for (blob in blobs.distinctBy(UploadBlobPath::digest)) {
                            launch {
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
            val blobs = mutableMapOf<String, Path>()
            var indexFile: ManifestList? = null
            var repositoriesFile: Repositories? = null
            var manifestJsonFile: ManifestJson? = null
            var ociLayoutFile: OciLayout? = null

            generateSequence(stream::getNextEntry).forEach { entry ->
                when {
                    entry.isDirectory -> stream.skip(entry.size)

                    entry.name.startsWith("blobs/sha256/") -> blobs[entry.name] =
                        stream.processBlobEntry(entry, tempDirectory)

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

            UploadContainerImage(
                index = indexFile,
                images = associateManifestsWithBlobs(indexFile, blobs),
                manifest = manifestJsonFile,
                repositories = repositoriesFile,
                layout = ociLayoutFile,
            )
        }
    }

    // Resolve blob path gracefully by searching for name part in blob names
    private fun resolveBlobPath(blobs: Map<String, Path>, namePart: String): Path? =
        blobs.entries.find { namePart in it.key }?.value

    private suspend fun resolveManifest(blobs: Map<String, Path>, manifestDigest: Digest): Manifest? {
        val blobPath = resolveBlobPath(blobs, "blobs/sha256/${manifestDigest.hash}") ?: return null

        val manifestStream = withContext(Dispatchers.IO) {
            SystemFileSystem.source(blobPath.toKotlinPath()).buffered().asInputStream()
        }
        return jacksonDeserializer<Manifest>().deserialize(manifestStream)
    }

    /**
     * Associates manifest with their layer blobs and config blobs
     */
    private suspend fun associateManifestsWithBlobs(
        index: ManifestList,
        blobPaths: Map<String, Path>,
    ): List<UploadSingleImage> = buildList {
        val notFoundAttestations = mutableListOf<Digest>()

        for ((mediaType, entryDigest, size, _) in index.manifests) {
            // resolve manifests from index
            val manifestBlobPath = resolveBlobPath(blobPaths, entryDigest.hash)
            if (manifestBlobPath == null) {
                notFoundAttestations.add(entryDigest)
                continue
            }
            val manifestBlob = UploadBlobPath(entryDigest, mediaType, manifestBlobPath, size)

            when (val manifest = resolveManifest(blobPaths, entryDigest)) {
                null -> continue

                is ManifestList -> {
                    // recursively resolve manifests
                    addAll(associateManifestsWithBlobs(manifest, blobPaths))
                    // add the manifest list itself as an image too, so that it can be referenced elsewhere
                    add(UploadSingleImage(manifest, entryDigest, listOf(manifestBlob)))
                }

                is ManifestSingle -> {
                    // associate manifests to their layer blobs and config blob
                    val blobs = (manifest.layers + manifest.config).map { layer ->
                        val blobPath = resolveBlobPath(blobPaths, layer.digest.hash)
                            ?: throw KircUploadException(
                                "Could not resolve blob for layer or config with digest '${layer.digest}' during upload",
                            )

                        UploadBlobPath(layer.digest, layer.mediaType, blobPath, layer.size)
                    }

                    add(UploadSingleImage(manifest, entryDigest, blobs + manifestBlob))
                }
            }
        }

        index.manifests.removeIf { it.digest in notFoundAttestations }
    }
}
