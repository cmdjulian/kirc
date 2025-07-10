package de.cmdjulian.kirc.impl.registry

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
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.coroutines.coroutineScope
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

internal interface UploadImageRegistryImpl : SuspendingContainerImageRegistryClient {

    override suspend fun upload(
        repository: Repository,
        reference: Reference,
        tar: Source,
    ): Digest = coroutineScope {
        // store data temporarily
        val tempDirectory = Path(
            SystemTemporaryDirectory,
            "${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)}--$repository--$reference",
        ).also(SystemFileSystem::createDirectories)

        // read from temp file and deserialize
        val uploadContainerImage = readFromTar(tar, tempDirectory)

        // upload architecture images
        try {
            uploadContainerImage.images.forEach { (manifest, manifestDigest, blobs) ->
                // todo async {
                blobs.forEach { blob ->
                    if (!existsBlob(repository, blob.digest)) {
                        val session = initiateBlobUpload(repository)

                        val source = SystemFileSystem.source(blob.path).buffered()

                        val endSession = uploadBlobChunks(session, source, blob.size)

                        finishBlobUpload(endSession, blob.digest)
                    }
                }

                uploadManifest(repository, manifestDigest, manifest)
            }
            // todo }.awaitAll()
        } finally {
            // clear up temporary blob files
            SystemFileSystem.list(tempDirectory).forEach(SystemFileSystem::delete)
            SystemFileSystem.delete(tempDirectory)
        }

        // upload index
        uploadManifest(repository, reference, uploadContainerImage.index)
    }

    override suspend fun upload(tar: Source): Digest {
        TODO("Not yet implemented")
    }

    private fun readFromTar(tarSource: Source, tempDirectory: Path) = tarSource.use { source ->
        TarArchiveInputStream(source.asInputStream()).use { stream ->
            val blobs = mutableMapOf<String, Path>()
            var index: ManifestList? = null
            var repositoriesFile: Repositories? = null
            var manifestJsonFile: ManifestJson? = null
            var layoutFile: OciLayout? = null

            generateSequence(stream::getNextEntry).forEach { entry ->
                // read data shortcut
                val readEntryData = { stream.readNBytes(entry.size.toInt()) }

                when {
                    entry.isDirectory -> readEntryData()

                    entry.name.contains("blobs/sha256/") -> {
                        val blobDigest = entry.name.removePrefix("blobs/sha256/")
                        val tempPath = Path(tempDirectory, blobDigest)
                        SystemFileSystem.sink(tempPath).buffered().also { path ->
                            path.write(stream.asSource(), entry.realSize)
                            path.flush()
                        }
                        blobs[entry.name] = tempPath
                    }

                    entry.name.contains("index.json") ->
                        index = jacksonDeserializer<ManifestList>().deserialize(readEntryData())

                    entry.name.contains("repositories") ->
                        repositoriesFile = jacksonDeserializer<Repositories>().deserialize(readEntryData())

                    entry.name.contains("manifest.json") ->
                        manifestJsonFile = jacksonDeserializer<ManifestJson>().deserialize(readEntryData())

                    entry.name.contains("oci-layout") ->
                        layoutFile = jacksonDeserializer<OciLayout>().deserialize(readEntryData())

                    else -> readEntryData()
                }
            }

            require(index != null) { "index should be present inside provided docker image" }
            require(layoutFile != null) { "'oci-layout' file should be present inside the provided docker image" }

            UploadContainerImage(
                index = index,
                images = associateManifestsWithBlobs(index, blobs),
                manifest = manifestJsonFile,
                repositories = repositoriesFile,
                layout = layoutFile,
            )
        }
    }

    private fun findBlobPath(blobs: Map<String, Path>, regex: String): Path {
        val blobPath = blobs.keys.first { blobName -> blobName.contains(regex) }
            .let(blobs::get)

        require(blobPath != null) { "Could not find blob with path '$blobPath' in deserialized list" }

        return blobPath
    }

    /**
     * Associates manifest with their layer blobs and config blobs
     */
    private fun associateManifestsWithBlobs(
        index: ManifestList,
        blobs: MutableMap<String, Path>,
    ): List<UploadSingleImage> {
        // find manifests from index
        val manifests = index.manifests.associate { manifest ->
            val blobPath = findBlobPath(blobs, "blobs/sha256/${manifest.digest.hash}")
            // We can read the data into memory because it is small enough
            val manifestSingle = jacksonDeserializer<ManifestSingle>()
                .deserialize(SystemFileSystem.source(blobPath).buffered().readByteArray())

            // clear up of read manifest file
            // SystemFileSystem.delete(blobPath)

            manifest.digest to manifestSingle
        }

        // associate manifests to their blobs and config
        return manifests.map { (digest, manifest) ->
            val layerBlobs = manifest.layers.map { layer ->
                val blobPath = findBlobPath(blobs, layer.digest.hash)
                UploadBlobPath(layer.digest, layer.mediaType, blobPath, layer.size)
            }
            val configBlob = manifest.config.let { config ->
                val blobPath = findBlobPath(blobs, config.digest.hash)
                // technically no layer blob but handled as blob when uploaded
                UploadBlobPath(config.digest, config.mediaType, blobPath, config.size)
            }
            val manifestBlob = digest.let {
                val manifestPath = findBlobPath(blobs, it.hash)
                val size = SystemFileSystem.metadataOrNull(manifestPath)!!.size
                UploadBlobPath(it, manifest.mediaType!!, manifestPath, size)
            }

            UploadSingleImage(manifest, digest, layerBlobs + configBlob + manifestBlob)
        }
    }
}