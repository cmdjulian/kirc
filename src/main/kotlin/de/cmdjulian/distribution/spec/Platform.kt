package de.cmdjulian.distribution.spec

private val platformRegex = Regex("^([a-zA-Z0-9]+)/([a-zA-Z0-9]+)$")

@JvmInline
value class Platform(private val value: String) {
    companion object {
        fun default() = Platform(PlatformResolver.resolve())
    }

    init {
        require(value.matches(platformRegex)) { "invalid platform string" }
    }

    override fun toString() = value
}

/**
 * Resolves current platform.
 */
private object PlatformResolver {
    private val os: String by lazy {
        if (System.getProperty("os.name")?.lowercase()?.contains("win") == true) "windows" else "linux"
    }

    private val arch: String by lazy {
        System.getProperty("os.arch") ?: "amd64"
    }

    fun resolve() = "$os/$arch"
}
