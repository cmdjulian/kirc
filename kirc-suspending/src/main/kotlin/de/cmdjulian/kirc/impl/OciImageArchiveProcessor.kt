package de.cmdjulian.kirc.impl

import de.cmdjulian.kirc.spec.ContainerImage
import de.cmdjulian.kirc.spec.LayerBlob
import de.cmdjulian.kirc.spec.ManifestJson
import de.cmdjulian.kirc.spec.Repositories
import de.cmdjulian.kirc.spec.UploadImage
import de.cmdjulian.kirc.spec.manifest.ManifestList
import de.cmdjulian.kirc.spec.manifest.ManifestListEntry
import de.cmdjulian.kirc.spec.manifest.ManifestSingle
import de.cmdjulian.kirc.spec.manifest.OciManifestListV1
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.InputStream
import java.io.OutputStream

internal object OciImageArchiveProcessor {

    // process for download

    fun writeToTar(outputStream: OutputStream, image: ContainerImage) {
        val manifestListEntry = ManifestListEntry(
            mediaType = "",
            digest = image.digest,
            size = 2,
            platform = ManifestListEntry.Platform(image.config.os, image.config.architecture, emptyList()),
        )
        val index = OciManifestListV1(2, OciManifestListV1.MediaType, listOf(manifestListEntry), mapOf())
        writeToTar(outputStream, index, listOf(image))
    }

    fun writeToTar(outputStream: OutputStream, index: ManifestList, images: List<ContainerImage>) =
        TarArchiveOutputStream(outputStream).use { tarOutput ->
            tarOutput.writeEntry("index.json", index)

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
            val blobs = mutableMapOf<String, ByteArray>()
            var index: ManifestList? = null
            var repositories: Repositories? = null
            var manifestJson: ManifestJson? = null

            while (entry != null) {
                val readEntryData = { stream.readNBytes(entry!!.size.toInt()) }
                when {
                    entry.isDirectory -> continue

                    entry.name.contains("blobs/sha256/") ->
                        blobs[entry.name] = readEntryData()

                    entry.name.contains("index.json") ->
                        index = jacksonDeserializer<ManifestList>().deserialize(readEntryData())

                    entry.name.contains("repositories") ->
                        repositories = jacksonDeserializer<Repositories>().deserialize(readEntryData())

                    entry.name.contains("manifest.json") ->
                        manifestJson = jacksonDeserializer<ManifestJson>().deserialize(readEntryData())

                    else -> readEntryData()
                }
                entry = stream.nextEntry
            }

            require(index != null) { "index should not be null" }

            associateManifestsWithBlobs(index, blobs)
        }
    }

    private fun findBlob(blobs: Map<String, ByteArray>, regex: String, notFoundMessage: String): ByteArray {
        val blob = blobs.keys.first { blobName -> blobName.contains(regex) }
            .let(blobs::get)

        require(blob != null) { notFoundMessage }

        return blob
    }

    /**
     * Associates manifest with their layer blobs and config blobs
     */
    private fun associateManifestsWithBlobs(
        index: ManifestList,
        blobs: MutableMap<String, ByteArray>,
    ): List<UploadImage> {
        // find manifests from index
        val manifests = index.manifests.associate { manifest ->
            val blob = findBlob(blobs, "blobs/sha256/${manifest.digest.hash}.json", "TODO")
            val manifestSingle = jacksonDeserializer<ManifestSingle>().deserialize(blob)
            manifestSingle to manifest.digest
        }

        // associate manifests to their blobs and config
        return manifests.map { (manifest, digest) ->
            val layerBlobs = manifest.layers.map { layer ->
                val blob = findBlob(blobs, layer.digest.hash, "TODO")
                LayerBlob(layer.digest, layer.mediaType, blob)
            }
            val configBlob = manifest.config.let { config ->
                val blob = findBlob(blobs, config.digest.hash, "TODO")
                // technically no layer blob but handled as blob when uploaded
                LayerBlob(config.digest, config.mediaType, blob)
            }

            UploadImage(manifest, digest, layerBlobs + configBlob)
        }
    }
}