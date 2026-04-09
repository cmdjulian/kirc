package de.cmdjulian.kirc.tar

import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.nio.file.Path

object BlockingImageExtractor {

    fun parse(input: InputStream, tempDirectory: Path): ContainerImageTar =
        runBlocking { ImageExtractor.parse(input, tempDirectory) }

    fun parse(path: Path, isGzipped: Boolean = false, tempDirectory: Path): ContainerImageTar =
        runBlocking { ImageExtractor.parse(path, isGzipped, tempDirectory) }

    fun parse(path: Path, isGzipped: Boolean = false): ContainerImageMetadata =
        runBlocking { ImageExtractor.parse(path, isGzipped) }
}
