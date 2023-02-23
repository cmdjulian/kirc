@file:Suppress("unused")

package de.cmdjulian.distribution.model

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
class DockerImageSlug(
    val registry: Registry = Registry("docker.io"),
    val repository: Repository,
    tag: Tag? = null,
    val digest: Digest? = null,
) {

    val tag = if (tag == null && digest == null) Tag("latest") else tag
    val reference = digest ?: this.tag!!

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

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is DockerImageSlug -> false
        registry != other.registry -> false
        repository != other.repository -> false
        reference != other.reference -> false
        else -> true
    }

    override fun hashCode(): Int {
        var result = registry.hashCode()
        result = 31 * result + repository.hashCode()
        result = 31 * result + reference.hashCode()
        return result
    }
}
