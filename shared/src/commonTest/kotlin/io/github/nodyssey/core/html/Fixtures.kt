package io.github.nodyssey.core.html

/**
 * Real NodeSeek pages captured for offline parser tests. Never hit the live site from a test —
 * the markup changes and Cloudflare will not answer a CI runner anyway.
 *
 * The files themselves are still files, under `src/commonTest/resources/fixtures`; what changed when
 * these tests became common is how they are read. Kotlin/Native has no classpath and no
 * `getResourceAsStream`, so the `generateFixtureSources` task compiles that directory into Kotlin
 * string constants and this reads those. Editing a fixture is still editing the HTML file.
 */
object Fixtures {
    fun load(name: String): String = requireNotNull(generatedFixture(name)) { "Missing fixture: $name" }
}
