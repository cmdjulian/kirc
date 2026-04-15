package de.cmdjulian.kirc.tar

import kotlinx.coroutines.runBlocking
import java.nio.file.Path

object BlockingImageExtractor {
    /**
     * Parses docker container image from [path] representing a tar file into an object Representation
     *
     * If [isGzipped] is true, the file will be treated as gzipped tar and decompressed before parsing
     */
    fun parse(path: Path, isGzipped: Boolean = false): ContainerImageMetadata =
        runBlocking { ImageExtractor.parse(path, isGzipped) }
}
