@file:OptIn(InternalKircApi::class)

package de.cmdjulian.kirc.tar

import de.cmdjulian.kirc.annotation.InternalKircApi
import de.cmdjulian.kirc.exception.KircException
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.impl.serialization.JsonMapper
import de.cmdjulian.kirc.impl.serialization.deserialize
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.manifest.DockerManifestListV1
import de.cmdjulian.kirc.spec.manifest.Manifest
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.io.files.Path as KotlinPath

/**
 * Helper class to extract certain parts of a docker image, e.g. Index, Manifest, Config
 *
 * @param path location of docker image
 * @param isGzipped optionally, to signalize tar archive is gzipped, and it needs to be unwrapped during extraction
 */
object ImageExtractor {

    suspend fun parse(source: Source): ContainerImageTar = source.use {
        parse(it)
    }

    /**
     * Performs a single sequential pass through [input], spilling blobs to [tempDirectory], then resolves and
     * returns a fully-parsed [ContainerImageTar].
     *
     * [input] - the tar source to read from (consumed exactly once)
     * [tempDirectory] - directory where blob files are spilled during parsing
     */
    suspend fun parse(input: InputStream, tempDirectory: Path): ContainerImageTar = input.use { stream ->
        TarArchiveInputStream(stream).use { stream ->
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

                    "repositories" == entry.name ->
                        repositoriesFile = stream.deserializeEntry<Repositories>(entry)

                    "manifest.json" == entry.name ->
                        manifestJsonFile = stream.deserializeEntry<ManifestJson>(entry)

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
            ContainerImageTar(
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
     * Returns the resolved images as well as the index provided, stripped from all attachments
     * (attestations, cache, etc.).
     *
     * [index] - the provided [ManifestList] for which manifests should be resolved
     * [blobPaths] - a mapping of blob digests to their source path
     */
    private suspend fun resolveManifestsAndBlobs(
        index: ManifestList,
        blobPaths: Map<Digest, Path>,
    ): Pair<ManifestList, List<ContainerImageSingle>> {
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
                    BlobPath(entryDigest, manifestEntry.mediaType, manifestBlobPath, manifestEntry.size)
                addAll(resolveManifestsRecursively(entryDigest, blobPaths, manifestBlob))
            }
        }
        val manifests = index.manifests.filterNot { it.digest in attachments }
        val resolvedIndex = when (index) {
            is DockerManifestListV1 -> index.copy(manifests = manifests)
            is OciManifestListV1 -> index.copy(manifests = manifests)
        }
        return resolvedIndex to manifestBlobs
    }

    private suspend fun resolveManifestsRecursively(
        entryDigest: Digest,
        blobPaths: Map<Digest, Path>,
        manifestBlob: BlobPath,
    ) = buildList {
        when (val manifest = resolveManifest(blobPaths, entryDigest)) {
            null -> Unit

            is ManifestList -> {
                // recursively resolve manifests
                val (processedManifestList, processedManifests) = resolveManifestsAndBlobs(manifest, blobPaths)
                addAll(processedManifests)
                // add the manifest list itself as an image too, so that it can be referenced elsewhere
                add(ContainerImageSingle(processedManifestList, entryDigest, listOf(manifestBlob)))
            }

            is ManifestSingle -> {
                // associate manifests to their layer blobs and config blob
                val blobs = (manifest.layers + manifest.config).map { layer ->
                    val blobPath = blobPaths[layer.digest] ?: throw KircException.CorruptArchiveError(
                        "Could not resolve blob for layer or " +
                            "config with digest '${layer.digest}' during upload",
                    )
                    BlobPath(layer.digest, layer.mediaType, blobPath, layer.size)
                }
                add(ContainerImageSingle(manifest, entryDigest, blobs + manifestBlob))
            }
        }
    }

    private suspend fun resolveManifest(blobs: Map<Digest, Path>, manifestDigest: Digest): Manifest? {
        val blobPath = blobs[manifestDigest] ?: return null

        val manifestStream = withContext(Dispatchers.IO) {
            SystemFileSystem.source(KotlinPath(blobPath.pathString)).buffered().asInputStream()
        }
        return runCatching {
            JsonMapper.deserialize<Manifest>(manifestStream)
        }.getOrElse {
            throw KircException.CorruptArchiveError("Could not deserialize manifest (digest=$manifestDigest)", it)
        }
    }
}
