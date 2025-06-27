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
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

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

    fun readFromTar(inputTarStream: InputStream) = inputTarStream.use { tarStream ->
        TarArchiveInputStream(tarStream).use { stream ->
            var entry: ArchiveEntry? = stream.nextEntry
            val blobs = mutableMapOf<String, Path>()
            var index: ManifestList? = null
            var repositoriesFile: Repositories? = null
            var manifestJsonFile: ManifestJson? = null
            var layoutFile: OciLayout? = null

            while (entry != null) {
                // read data shortcut
                val readEntryData = { stream.readNBytes(entry!!.size.toInt()) }
                // store data in file shortcut
                when {
                    entry.isDirectory -> readEntryData()

                    // todo
                    entry.name.contains("blobs/sha256/") -> {
                        val blobDigest = entry.name.removePrefix("blobs/sha256/")
                        val tempBlobPath = createTempFile("${OffsetDateTime.now()}-${blobDigest}", "").also { path ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var remaining = entry.size
                            val tempFileOutputStream = path.outputStream()
                            while (remaining > 0) {
                                val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                                val read = stream.read(buffer, 0, toRead)
                                if (read == -1) break
                                tempFileOutputStream.write(buffer, 0, read)
                                remaining -= read
                            }
                        }
                        blobs[entry.name] = tempBlobPath
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
                entry = stream.nextEntry
            }

            require(index != null) { "index should be present inside provided docker image" }
            require(layoutFile != null) { "layoutFile should be present inside provided docker image" }

            val singleImages = associateManifestsWithBlobs(index, blobs)
            UploadContainerImage(index, singleImages, manifestJsonFile, repositoriesFile, layoutFile)
        }
    }

    // todo
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
            val manifestSingle =
                jacksonDeserializer<ManifestSingle>().deserialize(blobPath.inputStream().readAllBytes())
            manifestSingle to manifest.digest
        }

        // associate manifests to their blobs and config
        return manifests.map { (manifest, digest) ->
            val layerBlobs = manifest.layers.map { layer ->
                val blob = findBlob(blobs, layer.digest.hash)
                UploadBlobPath(layer.digest, layer.mediaType, blob)
            }
            val configBlob = manifest.config.let { config ->
                val blob = findBlob(blobs, config.digest.hash)
                // technically no layer blob but handled as blob when uploaded
                UploadBlobPath(config.digest, config.mediaType, blob)
            }

            UploadSingleImage(manifest, digest, layerBlobs + configBlob)
        }
    }
}