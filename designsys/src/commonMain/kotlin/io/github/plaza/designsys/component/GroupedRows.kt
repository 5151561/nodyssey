package io.github.plaza.designsys.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaLayers
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
 * The common case of [GroupedListItem] with plain strings: icon, title, optional second line,
 * optional current value, chevron.
 *
 * [value] is the row's current state rendered at the trailing edge — "未开启", "3 人", an avatar. It is
 * deliberately separate from [subtitle]: a value that wrapped onto its own line stopped reading as
 * *this row's* state and started reading as another sentence.
 */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    titleColor: Color? = null,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GroupedListItem(
        first = first,
        last = last,
        modifier = modifier,
        onClick = onClick,
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = titleColor ?: MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent =
        icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent =
        if (value == null && trailing == null && !showChevron) {
            null
        } else {
            {
                GroupedRowTrailing(value = value, trailing = trailing, showChevron = showChevron)
            }
        },
    )
}

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
        value?.let {
            // Capped rather than weighted: the value takes what it needs and the title gets the rest;
            // the cap is what stops a long one from taking the lot.
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
