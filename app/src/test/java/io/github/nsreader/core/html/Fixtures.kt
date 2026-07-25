package io.github.nsreader.core.html

/**
 * Real NodeSeek pages captured for offline parser tests. Never hit the live site from a test —
 * the markup changes and Cloudflare will not answer a CI runner anyway.
 */
object Fixtures {
    fun load(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture: $name"
        }.bufferedReader().use { it.readText() }
}
