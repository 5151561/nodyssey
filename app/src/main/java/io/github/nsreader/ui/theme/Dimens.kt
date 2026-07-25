package io.github.nsreader.ui.theme

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
