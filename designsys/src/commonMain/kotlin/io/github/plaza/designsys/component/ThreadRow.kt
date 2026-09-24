package io.github.plaza.designsys.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.Spacing

/**
 * A thread's title in a list row — 收藏's and 历史's — at the one size and rhythm both state it in.
 *
 * [fontWeight] is a parameter because every row of the history is read by definition, and the weight
 * that marks an unread thread elsewhere would say nothing there.
 */
@Composable
fun ThreadRowTitle(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    Text(
        text = text,
        style = threadRowTitleStyle(),
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = fontWeight,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The title's type.
 *
 * Trimmed at the top so the glyphs start at the row's top edge instead of 3sp below it: 15/21 leaves
 * leading above the first line, and against the avatar beside it that gap read as the avatar sitting
 * higher than the title.
 *
 * A function rather than an inline `copy` because [listAvatarSize] measures a line of it.
 */
@Composable
private fun threadRowTitleStyle() =
    MaterialTheme.typography.titleMedium.copy(
        lineHeightStyle =
        LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.FirstLineTop,
        ),
    )

/**
 * The avatar every scrolling list in this app draws.
 *
 * One size for all of them, set by a thread row's type: an avatar dropped onto the title's cap line
 * ([AvatarCapOffset]) ends on the bottom of a tag under that title, and an avatar that changed size
 * from screen to screen would be the more obvious wrong — a reader sees the same faces on every list.
 *
 * Derived rather than declared. The row is built out of type — a 15/21 title, a 4dp gap, an 11/16
 * tag in a 1dp pill — so the distance from the top of the avatar to the bottom of that tag is a
 * number only the font knows, and it moves with the system font scale. Two probe measurements give
 * it exactly, on any device and at any scale, where a hard-coded `dp` would be right on the phone it
 * was picked on and wrong on the next one.
 *
 * The probe is a hanzi because the title beside it almost always is one, and the CJK fallback font
 * is the taller of the two: a Latin-only title measures a couple of dp shorter, and on one of those
 * rows the avatar overhangs the tag by that much. Sizing for the common row is the trade — the
 * alternative is an avatar that changes size from row to row.
 */
@Composable
fun listAvatarSize(): Dp {
    val measurer = rememberTextMeasurer()
    val titleStyle = threadRowTitleStyle()
    val tagStyle = LocalTextStyle.current.merge(tonalTagTextStyle())
    val density = LocalDensity.current
    return remember(measurer, titleStyle, tagStyle, density) {
        val title = measurer.measure(AVATAR_PROBE, titleStyle).size.height
        val tag = measurer.measure(AVATAR_PROBE, tagStyle).size.height
        with(density) { (title + tag).toDp() } +
            TonalTagVerticalPadding * 2 +
            Spacing.xs -
            AvatarCapOffset
    }
}

/** One glyph, measured only for its height — see [listAvatarSize]. */
private const val AVATAR_PROBE = "字"

/**
 * How far below the title's line box its cap line starts, which [listAvatarSize] takes off the
 * avatar: even with the first line's leading trimmed, a 15sp line box starts a few pixels above the
 * tallest glyph — ascent is not cap height. Measured at 15sp.
 */
internal val AvatarCapOffset = 5.dp
