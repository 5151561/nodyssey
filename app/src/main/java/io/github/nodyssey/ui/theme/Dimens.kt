package io.github.nodyssey.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val avatarList = 34.dp
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
