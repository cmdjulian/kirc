@file:Suppress("unused")

package de.cmdjulian.distribution.model.oci

/**
 * Parses a docker image name from a provided string.
 *
 *
 *     `name`,
 *     `name:tag`,
 *     `path/name`,
 *     `path/name:tag`,
 *     `some.registry/name`,
 *     `some.registry/name:tag`,
 *     `some.registry/path/name`,
 *     `some.registry/path/name:tag`,
 *     `some.registry/path/name@sha256:abcdef...`,
 *     `some.registry/path/name:tag@sha256:abcdef...`
 *
 * etc.
 */
data class DockerImageSlug(
    val registry: Registry = Registry("docker.io"),
    val repository: Repository,
    val tag: Tag? = null,
    val digest: Digest? = null
) {

    val reference: Reference = digest ?: tag ?: Tag("latest")

    companion object {
        @JvmStatic
        fun parse(image: String): DockerImageSlug {
            val slashIndex = image.indexOf('/')
            val isRegistryMissing = slashIndex == -1 ||
                "." !in image.substring(0, slashIndex) &&
                ":" !in image.substring(0, slashIndex) &&
                image.substring(0, slashIndex) != "localhost"
            val remoteName: String = if (isRegistryMissing) image else image.substring(slashIndex + 1)

            val registry = if (isRegistryMissing) null else Registry(image.substring(0, slashIndex))
            val (repository, tag, digest) = parseRepositoryAndVersion(remoteName)

            return when (registry) {
                null -> DockerImageSlug(repository = repository, tag = tag, digest = digest)
                else -> DockerImageSlug(registry, repository, tag, digest)
            }
        }

        private fun parseRepositoryAndVersion(remoteName: String) = when {
            '@' in remoteName -> {
                val repository: String
                val tag: String?
                val parts = remoteName.split('@').toTypedArray()
                // If a Tag is present remove it, because content is already identified by hash
                repository = if (':' in parts[0]) {
                    val subParts = parts[0].split(':').toTypedArray()
                    tag = subParts[1]
                    subParts[0]
                } else {
                    tag = null
                    parts[0]
                }
                Triple(Repository(repository), tag?.let(::Tag), Digest(parts[1]))
            }

            ':' in remoteName -> {
                val parts = remoteName.split(':').toTypedArray()
                Triple(Repository(parts[0]), Tag(parts[1]), null)
            }

            else -> Triple(Repository(remoteName), null, null)
        }
    }

    override fun toString(): String {
        fun tagComponent() = when {
            tag != null -> tag.separator + tag.toString()
            digest == null -> ":latest"
            else -> ""
        }

        fun digestComponent() = when (digest) {
            null -> ""
            else -> digest.separator + digest.toString()
        }

        return registry.toString() + '/' + repository.toString() + tagComponent() + digestComponent()
    }
}
