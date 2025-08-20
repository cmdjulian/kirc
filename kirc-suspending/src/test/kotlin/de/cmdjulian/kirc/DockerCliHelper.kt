package de.cmdjulian.kirc

import de.cmdjulian.kirc.client.BlockingContainerImageClientFactory
import de.cmdjulian.kirc.client.BlockingContainerImageRegistryClient
import de.cmdjulian.kirc.client.RegistryCredentials
import de.cmdjulian.kirc.image.Digest
import de.cmdjulian.kirc.image.Reference
import de.cmdjulian.kirc.image.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.asInputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.net.URI

class DockerRegistryCliHelper(addressName: String, credentials: RegistryCredentials) {

    private data class UploadReference(val repository: Repository, val reference: Reference)

    private var client: BlockingContainerImageRegistryClient =
        BlockingContainerImageClientFactory.create(url = URI.create(addressName), credentials = credentials)
    private val images = mutableListOf<UploadReference>()

    fun pushImage(repository: Repository, reference: Reference, path: String): Digest {
        images.add(UploadReference(repository, reference))
        val source = runBlocking(Dispatchers.IO) { SystemFileSystem.source(Path(path)) }
        return client.upload(repository, reference, source.buffered().asInputStream())
    }

    fun deleteAll() {
        images.forEach { (repository, reference) ->
            try {
                client.manifestDelete(repository, reference)
            } catch (_: Exception) {
            }
        }
        images.clear()
    }
}