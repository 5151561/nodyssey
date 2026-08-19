package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * One thread, as every list of threads draws it: avatar, title, one 12sp meta line.
 *
 * The title is the only thing with visual weight; everything else is metadata under it. That is the
 * whole design of these lists — nine rows fit on an 800dp screen and the title is legible in every
 * one of them.
 *
 * Shared rather than copied because a feed and a history list the same objects: two lists of threads
 * that round their gutters differently read as two different apps, and that is exactly what happened
 * when this app's history screen was built out of `ListItem`. Everything that varies between the
 * two — a pin instead of an avatar, a lock beside the title, which counts go on the meta line — is a
 * slot here, so a caller can differ without re-deciding the geometry.
 *
 * [supporting] is a third line under the meta, for the one thing a row has to say about *itself*
 * rather than about the thread — 收藏 uses it for 「离线版落后 3 条回复」 and for a failed download's
 * reason. It is its own line rather than another item in [meta] because the meta is a `FlowRow`: a
 * sentence dropped in there queues up beside the board tag and wraps mid-phrase.
 *
 * [trailing] is the row's own state at the end of the line — a download state, a count, a switch.
 * The row's end padding tightens when one is present, because a trailing block brings its own
 * optical margin and 16dp on top of it pushed the state off the edge of a 360dp screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    /** Long-press, for a list that can be multi-selected. Null keeps the row a plain `clickable`. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    supporting: (@Composable ColumnScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    meta: @Composable FlowRowScope.() -> Unit,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .let { base ->
                if (onLongClick == null) {
                    base.clickable(onClick = onClick)
                } else {
                    // Not a raw `pointerInput`: this is what gives the press a ripple and gives
                    // TalkBack a long-click action it can announce and perform.
                    base.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel,
                    )
                }
            }.background(containerColor)
            .padding(
                start = 14.dp,
                end = if (trailing == null) Spacing.lg else 10.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading()
        // Weighted so a trailing block keeps its width: without it the title column claims
        // everything left over and the state at the end of the row is measured at zero.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, content = title)
            // Flows rather than clips so that a large system font wraps the meta onto a second line
            // instead of pushing the timestamp — the single most useful item there — off the edge.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                content = meta,
            )
            supporting?.invoke(this)
        }
        trailing?.invoke()
    }
}

/**
 * The thread title, at the one size and rhythm every list states it in.
 *
 * [color] and [fontWeight] are parameters because a read thread in the feed is dimmed by both, while
 * every row of the history is read by definition and dimming them all would say nothing.
 */
@Composable
fun ThreadRowTitle(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    Text(
        text = text,
        style = threadRowTitleStyle(),
        color = color,
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
 * One size for all of them, and [ThreadRow] is the one that sets it: its bottom edge lands on the
 * bottom of the meta line's tag, which is the only place in the app where an avatar has something
 * to line up with. Notifications and the block list have no such anchor, and an avatar that changed
 * size from screen to screen would be the more obvious wrong of the two — a reader sees the same
 * faces on all three lists.
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
 * Drops the avatar onto the title's cap line.
 *
 * Even with the first line's leading trimmed, a 15sp line box still starts a few pixels above the
 * tallest glyph — ascent is not cap height — so a top-aligned avatar reads as floating higher than
 * the title next to it. Measured at 15sp on the row as built. An offset rather than padding so the
 * row keeps its height and the list still fits nine of them on a 800dp screen.
 */
val AvatarCapOffset = 5.dp
