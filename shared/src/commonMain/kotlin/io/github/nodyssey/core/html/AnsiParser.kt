package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Recovers the terminal output NodeSeek hides inside `<code class="language-ansi">`.
 *
 * The site cannot put a raw `ESC` in its HTML, so it encodes every control character as an empty
 * `<span data-ansicode="27">` and leaves the rest of the sequence as ordinary text. Jsoup's
 * `wholeText()` drops empty elements, which is why reading the code element as text yields a stream
 * with the escapes missing and their parameters left behind — the literal `[36m` that leaks into
 * the post body today. The fix is to read the element, not its text: [sourceOf] walks the children
 * and turns each `data-ansicode` back into the character it stands for.
 *
 * Only that recovery is here. What the escapes then *mean* is the terminal's grammar rather than
 * NodeSeek's, and lives in [io.github.plaza.core.ansi.AnsiDecoder].
 */
object AnsiParser {

    private const val CONTROL_CODE_ATTR = "data-ansicode"

    /**
     * Reassembles the escape-bearing source of a `<pre>`/`<code>` element.
     *
     * `internal` for the reason [RichContentParser.parse] gives: the parameter is a Ksoup type, and
     * Ksoup is an `implementation` dependency no consumer compiles against.
     */
    internal fun sourceOf(element: Element): String = buildString { appendSource(element) }

    private fun StringBuilder.appendSource(node: Node) {
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> append(child.getWholeText())

                is Element -> {
                    val code = child.attr(CONTROL_CODE_ATTR).toIntOrNull()
                    // These carry the control character in an attribute and are empty otherwise, so
                    // recursing into one would add nothing.
                    if (code != null) append(code.toChar()) else appendSource(child)
                }

                else -> Unit
            }
        }
    }
}
