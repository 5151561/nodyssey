package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.Spacing

/**
 * One row's outline within its group card: the card's 24dp at the group's outer corners, square at
 * the seams. See [GroupedListItem] and [groupSlice], which are the only things that should need it.
 */
fun groupShape(first: Boolean, last: Boolean): Shape =
    RoundedCornerShape(
        topStart = if (first) GROUP_OUTER_RADIUS else GROUP_SEAM_RADIUS,
        topEnd = if (first) GROUP_OUTER_RADIUS else GROUP_SEAM_RADIUS,
        bottomEnd = if (last) GROUP_OUTER_RADIUS else GROUP_SEAM_RADIUS,
        bottomStart = if (last) GROUP_OUTER_RADIUS else GROUP_SEAM_RADIUS,
    )

private val GROUP_OUTER_RADIUS = 24.dp
private val GROUP_SEAM_RADIUS = 0.dp

/**
 * How much of the row a value may claim before it starts ellipsizing.
 *
 * Generous for what these rows actually hold — 「30 天」, 「未开启」, 「300 条」 — and the point of it is
 * only that some number exists, so a row handed an unexpectedly long one keeps its title readable.
 */
private val VALUE_MAX_WIDTH = 120.dp

/**
 * The heading over a group — a settings block, a day of notifications, a sheet's section, a field.
 * Material has no heading component, so this is a [Text] carrying `heading()` semantics.
 *
 * One size and weight everywhere; what varies is [color] — primary where the label names a block of
 * settings, `onSurfaceVariant` where it only says where one run of the same list ends — and
 * [contentPadding], which defaults to the gap a label keeps above a group card.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = SectionLabelPadding,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = modifier.padding(contentPadding).semantics { heading() },
    )
}

private val SectionLabelPadding = PaddingValues(start = Spacing.md, top = 10.dp, bottom = 2.dp)

/** Small print under a group — what a setting cannot do, what a field accepts. */
@Composable
fun SectionNote(
    text: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.md),
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(contentPadding),
    )
}

/**
 * A titled run of [SectionNote]s under a page's last group — what a feature cannot do, and why. The
 * title reads as a heading to a screen reader; on screen it is the same small print as the lines.
 */
@Composable
fun SectionNotes(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionNote(title, Modifier.semantics { heading() })
        lines.forEach { SectionNote(it) }
    }
}

/**
 * The common case of [GroupedListItem] with plain strings: icon, title, optional second line,
 * optional current value, optional control, chevron.
 *
 * [value] is the row's current state rendered at the trailing edge — "未开启", "3 人", an avatar. It is
 * deliberately separate from [subtitle]: a value that wrapped onto its own line stopped reading as
 * *this row's* state and started reading as another sentence.
 *
 * [selected], [checked] / [onCheckedChange] and [enabled] are [GroupedListItem]'s, passed through; a
 * switch row carries its [GroupedListItemSwitch] as [trailing].
 */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = icon?.let { { Icon(it, contentDescription = null) } },
    subtitle: String? = null,
    /** For a subtitle that is an address or a value to be read character by character — a URL. */
    subtitleMonospace: Boolean = false,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    /**
     * Tints the title and the icon together, for the one destructive row in a group (退出登录) — the
     * same shape as its neighbours, differing only in colour.
     */
    contentColor: Color = Color.Unspecified,
    /**
     * Says the row opens a page of its own. Pass false for a row that acts in place — 清除缓存,
     * 退出登录 — where a chevron would promise a screen that never comes.
     */
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GroupedListItem(
        first = first,
        last = last,
        modifier = modifier,
        onClick = onClick,
        selected = selected,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = groupedListItemColors(contentColor),
        headlineContent = { Text(text = title, style = groupedRowTitleStyle()) },
        supportingContent =
        subtitle?.let {
            {
                val style = LocalTextStyle.current
                Text(text = it, style = if (subtitleMonospace) style.copy(fontFamily = FontFamily.Monospace) else style)
            }
        },
        leadingContent = leading,
        trailingContent = { GroupedRowTrailing(value = value, trailing = trailing, showChevron = showChevron) },
    )
}

/** A [GroupedRow]'s title: 15sp medium, one step under a list title so eight rows still fit a screen. */
@Composable
fun groupedRowTitleStyle(): TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)

/**
 * A row's end: its current value, any control, and the chevron that says the row opens something.
 * Shared by every grouped row so the three always sit in the same order at the same spacing.
 */
@Composable
fun GroupedRowTrailing(
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Both in the row's content colour rather than a fixed `onSurfaceVariant`: that is what the
        // trailing slot already provides while the row is enabled, and it dims with a disabled row.
        value?.let {
            // Capped rather than weighted: the value takes what it needs and the title gets the rest;
            // the cap is what stops a long one from taking the lot.
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH),
            )
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
