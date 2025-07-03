package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.OciLayout
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.UploadBlobPath
import de.cmdjulian.kirc.spec.UploadContainerImage
import de.cmdjulian.kirc.spec.UploadSingleImage
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.OutputStream

internal object ImageArchiveProcessor {

    // process for download

    fun writeToTar(
        outputStream: OutputStream,
        index: ManifestList,
        manifestJson: ManifestJson,
        repositories: Repositories,
        vararg images: ContainerImage,
    ) = TarArchiveOutputStream(outputStream).use { tarOutput ->
        tarOutput.writeEntry("index.json", index)
        tarOutput.writeEntry("manifest.json", manifestJson)
        tarOutput.writeEntry("repositories", repositories)
        tarOutput.writeEntry("oci-layout", OciLayout("1.0.0"))

        images.forEach { image -> tarOutput.writeImage(image) }
    }

    private fun TarArchiveOutputStream.writeImage(image: ContainerImage) {
        writeEntry("blobs/sha256/${image.digest.hash}", image.manifest)
        writeEntry("blobs/sha256/${image.manifest.config.digest.hash}.json", image.config)
        image.blobs.forEach { layerBlob ->
            writeEntry("blobs/sha256/${layerBlob.digest.hash}", layerBlob.data)
        }
    }

    private fun TarArchiveOutputStream.writeEntry(name: String, data: Any) {
        val indexEntry = TarArchiveEntry(name)
        val indexBytes = JsonMapper.writeValueAsBytes(data)
        putArchiveEntry(indexEntry)
        write(indexBytes)
        closeArchiveEntry()
    }

    // Process for upload

    fun readFromTar(tarSource: Source, tempDirectory: Path) = tarSource.use { source ->
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
    
    private fun findBlob(blobs: Map<String, Path>, regex: String): Path {
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
            val blobPath = findBlob(blobs, "blobs/sha256/${manifest.digest.hash}")
            // We can read the data into memory because it is small enough
            val manifestSingle = jacksonDeserializer<ManifestSingle>()
                .deserialize(SystemFileSystem.source(blobPath).buffered().readByteArray())

            // clear up of read manifest file
            SystemFileSystem.delete(blobPath)

            manifest.digest to manifestSingle
        }

        // associate manifests to their blobs and config
        return manifests.map { (digest, manifest) ->
            val layerBlobs = manifest.layers.map { layer ->
                val blob = findBlob(blobs, layer.digest.hash)
                UploadBlobPath(layer.digest, layer.mediaType, blob, layer.size)
            }
            val configBlob = manifest.config.let { config ->
                val blob = findBlob(blobs, config.digest.hash)
                // technically no layer blob but handled as blob when uploaded
                UploadBlobPath(config.digest, config.mediaType, blob, config.size)
            }

            UploadSingleImage(manifest, digest, layerBlobs + configBlob)
        }
    }
}