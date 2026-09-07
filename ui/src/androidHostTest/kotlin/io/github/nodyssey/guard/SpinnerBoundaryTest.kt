package io.github.nodyssey.guard

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Every indeterminate spinner in the app goes through `PlazaSpinner`.
 *
 * This one is a ratchet rather than a style rule, because the thing it protects cannot be seen in a
 * review. Material's indeterminate indicators drive themselves — `CircularProgressIndicator` from a
 * `rememberInfiniteTransition`, the Expressive one from an `Animatable` in a `LaunchedEffect` —
 * neither of them reading `MaterialTheme.motionScheme`. `PlazaTheme` snaps that scheme for 墨水屏模式
 * and for the OS's 移除动画, and both settings go straight past these two components. So a spinner
 * added directly is not a small inconsistency: it is the one thing left on the screen still
 * repainting, on a panel where repainting is the whole cost.
 *
 * Determinate indicators are exempt and stay exempt: given a `progress` lambda, the same component
 * draws an arc at the fraction it is handed and animates nothing by itself. Two upload rings rely on
 * that, and wrapping them would have replaced the progress they exist to show with a still circle.
 */
class SpinnerBoundaryTest {
    @Test
    fun `no screen draws an indeterminate Material spinner directly`() {
        val root = repositoryRoot()
        val offenders = mutableListOf<String>()
        var determinate = 0
        var scanned = 0

        for (module in listOf("ui", "designsys")) {
            for (file in productionSources(File(root, module))) {
                if (file.name in ALLOWED) continue
                scanned++
                val source = codeOnly(file.readText())
                val path = file.relativeTo(root).invariantSeparatorsPath

                for (call in callsTo("CircularProgressIndicator", source)) {
                    if ("progress =" in call.arguments) {
                        determinate++
                    } else {
                        offenders += "$path:${call.line}  CircularProgressIndicator without progress ="
                    }
                }
                for (call in callsTo("LoadingIndicator", source)) {
                    offenders += "$path:${call.line}  LoadingIndicator"
                }
            }
        }

        // A guard that scanned nothing would pass forever; so would one whose exemption stopped
        // matching anything, which is how "determinate is exempt" quietly becomes "nothing is
        // checked" after a refactor.
        assertTrue("the guard scanned no sources", scanned > 0)
        assertTrue("no determinate indicator left — has the exemption gone stale?", determinate > 0)

        if (offenders.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("These draw an indeterminate spinner that no motion setting can stop.")
                    appendLine("Use PlazaSpinner (or PlazaLoadingIndicator for a whole screen) instead:")
                    appendLine()
                    offenders.sorted().forEach { appendLine("  $it") }
                },
            )
        }
    }

    private data class Call(val line: Int, val arguments: String)

    private companion object {
        /** The wrapper itself, which has to name both components to be able to draw them. */
        val ALLOWED = setOf("PlazaSpinner.kt")

        /**
         * Calls to [name] in already-decommented source, each with its balanced argument list.
         *
         * Balanced rather than "up to the next newline": most of these calls are multi-line, and
         * `progress = { … }` — the whole exemption — is inside a lambda with braces of its own.
         */
        fun callsTo(name: String, source: String): List<Call> {
            val calls = mutableListOf<Call>()
            val pattern = Regex("""(?<![A-Za-z0-9_.])$name\(""")
            for (match in pattern.findAll(source)) {
                val open = match.range.last
                var depth = 0
                var close = open
                for (index in open until source.length) {
                    when (source[index]) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                    if (depth == 0) {
                        close = index
                        break
                    }
                }
                calls +=
                    Call(
                        line = source.take(match.range.first).count { it == '\n' } + 1,
                        arguments = source.substring(open + 1, close),
                    )
            }
            return calls
        }
    }
}
