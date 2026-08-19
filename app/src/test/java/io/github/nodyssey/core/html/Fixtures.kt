package io.github.nodyssey.core.html

/**
 * The same captured pages the parser tests read, for the tests that stayed on this side.
 *
 * A second, one-line copy of a helper rather than a shared one: the files are shared — this module's
 * unit tests take `:shared/src/commonTest/resources` as a resource root, see `app/build.gradle.kts` —
 * but the way they are read is not. A common test cannot use the classpath at all, so the `:shared`
 * copy of this name reads generated Kotlin constants instead. Here, on the JVM, a resource stream is
 * still the honest way to read a file.
 */
object Fixtures {
    fun load(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture: $name"
        }.bufferedReader().use { it.readText() }
}
