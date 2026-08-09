package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    meta: @Composable FlowRowScope.() -> Unit,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(containerColor)
            .padding(start = 14.dp, end = Spacing.lg, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading()
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, content = title)
            // Flows rather than clips so that a large system font wraps the meta onto a second line
            // instead of pushing the timestamp — the single most useful item there — off the edge.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                content = meta,
            )
        }
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
        // Trimmed at the top so the glyphs start at the row's top edge instead of 3sp below it:
        // 15/21 leaves leading above the first line, and against the avatar beside it that gap read
        // as the avatar sitting higher than the title.
        style =
        MaterialTheme.typography.titleMedium.copy(
            lineHeightStyle =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.FirstLineTop,
            ),
        ),
        color = color,
        fontWeight = fontWeight,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Drops the avatar onto the title's cap line.
 *
 * Even with the first line's leading trimmed, a 15sp line box still starts a few pixels above the
 * tallest glyph — ascent is not cap height — so a top-aligned avatar reads as floating higher than
 * the title next to it. Measured at 15sp on the row as built. An offset rather than padding so the
 * row keeps its height and the list still fits nine of them on a 800dp screen.
 */
val AvatarCapOffset = 5.dp
