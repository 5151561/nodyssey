package io.github.plaza.designsys.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 8dp spacing grid, plus the two half-steps a dense list actually needs.
 *
 * Named rather than inlined so a density change is one edit — the list's whole reason to exist is
 * fitting nine rows on a 800dp screen, and that target is set by these numbers.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Sizes {
    /** Anything tappable must clear this; Material's own minimum and the brief's hard requirement. */
    val minTouchTarget = 48.dp

    val avatarComment = 28.dp
    val avatarOriginalPost = 40.dp
    val avatarProfile = 64.dp

    /** Beyond this a body column stops being comfortable to read, so it stops growing. */
    val readableContentWidth = 640.dp

    /** Tall screenshots are common on this forum and would otherwise fill several screens. */
    val maxInlineImageHeight = 520.dp
}

/**
 * Caps the content at [Sizes.readableContentWidth] and centres what is left over.
 *
 * Applied to the scrolling column itself rather than to each block of text inside it. Capping the
 * text alone still let the rows, dividers and row backgrounds run the full width of a tablet, so a
 * 1000dp window got a 640dp paragraph pinned to the left of a 1000dp divider.
 *
 * The three-step order is load-bearing: `fillMaxWidth` claims the window, `wrapContentWidth`
 * releases the minimum-width constraint that would otherwise force the child to fill it, and only
 * then can `widthIn` actually bind. Reversed, the cap is silently ignored.
 */
fun Modifier.readableWidth(): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = Sizes.readableContentWidth)

/**
 * Takes a `Scaffold`'s content padding *and* the keyboard, without paying for the bottom twice.
 *
 * `Scaffold` hands out padding that already contains the navigation bar, and `imePadding` asks for
 * the keyboard's full height — which is measured from the bottom of the window, navigation bar
 * included. Applied one after the other they stack, and whatever sits at the bottom of the screen
 * floats a navigation bar's worth above the keyboard instead of resting on it. That gap is the
 * recurring bug: every editor that pins a formatting strip to the keyboard has had it, and it looks
 * enough like a design decision that it survives review.
 *
 * [consumeWindowInsets] is the missing step. It tells the `imePadding` below it that the bottom
 * inset has already been handled, so the keyboard padding applies only what is left.
 *
 * Only for content inside a `Scaffold`. A bottom sheet gets no such padding and wants a bare
 * `imePadding()` — see the reply composer, which is why that one has always sat flush.
 */
fun Modifier.paddingWithKeyboard(padding: PaddingValues): Modifier =
    this
        .padding(padding)
        .consumeWindowInsets(padding)
        .imePadding()

/**
 * Fades the bottom [height] of the content into [color], and draws nothing there on paper.
 *
 * Three copies of this had grown — a cropped image's overlay, the privacy screen's scroll hint, a
 * quoted comment's cut-off body — and each one had decided the e-paper question for itself. That
 * is the part worth having in one place: a smooth ramp is the worst thing this app can ask a
 * sixteen-level panel to draw, and it dithers it in the middle of a post rather than off in a
 * settings screen. [einkRule] is for the one case that cannot simply drop the fade: a cropped
 * image has no other edge, so on paper it gets a flat plate with a rule along the top instead —
 * the same message, one tone. Everywhere else the words underneath already say it, and paper gets
 * nothing.
 *
 * The brush is built in [drawWithCache], not per frame: it depends on the size, and a preview
 * redraws on every scroll, ripple and image load.
 *
 * [above] puts the fade over the content (an image, which has to be dimmed) rather than under it
 * (text, which has to stay legible while the background rises behind it).
 */
fun Modifier.fadeToBackground(
    color: Color,
    height: Dp,
    eink: Boolean,
    above: Boolean = false,
    einkRule: Color? = null,
): Modifier =
    this.drawWithCache {
        val band = height.toPx().coerceAtMost(size.height)
        val brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0f), color),
            startY = size.height - band,
            endY = size.height,
        )
        onDrawWithContent {
            if (!above) {
                if (!eink) drawRect(brush)
                drawContent()
                return@onDrawWithContent
            }
            drawContent()
            if (eink) {
                if (einkRule != null) {
                    drawRect(color = color, topLeft = Offset(0f, size.height - band), size = Size(size.width, band))
                    drawLine(
                        color = einkRule,
                        start = Offset(0f, size.height - band),
                        end = Offset(size.width, size.height - band),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                return@onDrawWithContent
            }
            drawRect(brush)
        }
    }
