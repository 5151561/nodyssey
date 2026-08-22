package io.github.plaza.designsys.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult

/**
 * Tells whoever will open a link that a link is *about* to be opened.
 *
 * A browser handed a URL cold does everything in series — start its process, bring up its network
 * stack, resolve the host, open the socket, negotiate TLS — and only then asks for the page. A
 * browser told the URL a moment early does the first four while the finger is still on the glass,
 * which is most of what "the same link is instant in the browser" is actually measuring: over there
 * the process was already running and the connection pool already warm.
 *
 * The seam is here rather than in the handler because only the *text* knows a press happened.
 * `LinkAnnotation` reports a click, which is too late to be worth anything — by then the launch is
 * the next statement. See [prefetchLinksOnPress].
 *
 * The default does nothing, which is the honest answer on a platform with no such notion and the
 * one every preview and test gets for free.
 */
fun interface LinkPrefetcher {
    fun prefetch(url: String)
}

/** The prefetcher in force, swapped in at the composition root beside `LocalUriHandler`. */
val LocalLinkPrefetcher = staticCompositionLocalOf { LinkPrefetcher {} }

/**
 * Hands [LocalLinkPrefetcher] the URL under the finger the moment the finger lands on it.
 *
 * Only the link actually pressed, and only once it is pressed. The cheaper thing to write would be
 * to prefetch every link in a post as it renders, and it would be faster still — but a forum post is
 * other people's links, and warming them all means the reader's IP arriving at twenty hosts they
 * never opened. A press is the reader having chosen.
 *
 * [layout] is read rather than captured because the result arrives after the first composition and
 * changes on every reflow; the modifier has to see the current one, not the one that existed when
 * the gesture handler was installed.
 *
 * The down event is watched on [PointerEventPass.Initial] and left unconsumed, so this sees the press
 * before the text's own link handling does and takes nothing away from it — the tap still lands, the
 * link still opens, and a press that turns into a scroll has merely warmed a connection nobody used.
 */
@Composable
fun Modifier.prefetchLinksOnPress(
    text: AnnotatedString,
    layout: () -> TextLayoutResult?,
): Modifier {
    val prefetcher = LocalLinkPrefetcher.current
    return this.pointerInput(text, prefetcher) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            text.linkAt(down.position, layout())?.let(prefetcher::prefetch)
        }
    }
}

/**
 * The URL of the link drawn under [position], or null where there is no link — or no text.
 *
 * `getOffsetForPosition` answers with the *nearest* offset rather than admitting a miss, so a press
 * in the margin past the end of a line comes back as that line's last character. The line's own
 * horizontal bounds are checked first, which is what keeps a tap on the empty half of a short line
 * from warming whatever link happens to end it.
 */
private fun AnnotatedString.linkAt(
    position: Offset,
    layout: TextLayoutResult?,
): String? {
    val result = layout ?: return null
    val line = result.getLineForVerticalPosition(position.y)
    if (position.x < result.getLineLeft(line) || position.x > result.getLineRight(line)) return null
    val offset = result.getOffsetForPosition(position)
    return getLinkAnnotations(offset, offset)
        .firstNotNullOfOrNull { (it.item as? LinkAnnotation.Url)?.url }
}
