package io.github.nodyssey.guard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The machine half of a rule that has so far held by discipline alone: a screen talks to a
 * repository, never to the storage or transport underneath it.
 *
 * The rule matters because `:shared` cannot help but expose these packages — Room entities and DAOs
 * are its `api` surface, so all of `data.local` is on every screen's compile classpath, and the
 * compiler would accept a `feedDao()` call from a composable without a murmur. At the time this
 * guard was written the count of such imports was zero; the guard exists so that the first one is a
 * failed test naming the file, not a pattern someone finds three screens deep a year later.
 *
 * Import lines rather than the lexer next door: a banned type can only be used by importing it or
 * by fully qualifying it, and a fully-qualified `io.github.nodyssey.data.local.FeedDao` in screen
 * code would not survive review even without this test. Imports are where it would slip in quietly.
 */
class UiImportBoundaryTest {
    private val bannedPrefixes =
        listOf(
            // Room's tables and DAOs. A screen that reads one bypasses the repository contract that
            // makes offline behaviour and sign-out cleanup enforceable in one place.
            "io.github.nodyssey.data.local.",
            // The transport. Screens see suspend repository calls; which HTTP client answers them is
            // an Android detail two modules down.
            "okhttp3.",
            // The HTML parser. Parsing lives in `:shared` behind internal entry points on purpose —
            // see the note on the ksoup dependency in `shared/build.gradle.kts`.
            "com.fleeksoft.ksoup",
        )

    @Test
    fun `ui production sources do not import storage, transport or parsing types`() {
        val module = File(repositoryRoot(), "ui")
        val violations =
            productionSources(module).flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val imported = line.trim().removePrefix("import ").takeIf { it != line.trim() }
                    if (imported != null && bannedPrefixes.any { imported.startsWith(it) }) {
                        "${file.relativeTo(module.parentFile)}:${index + 1}: import $imported"
                    } else {
                        null
                    }
                }
            }

        assertEquals(
            "A screen imports below the repository layer. Route the data through a repository " +
                "interface on AppContainer instead — the classpath allows this, the architecture does not.",
            emptyList<String>(),
            violations,
        )
    }
}
