package io.github.nodyssey.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.theme.Spacing

/**
 * The grouped rounded list the settings and tools screens are built from.
 *
 * The shape is the grouping: the first and last row round to 18dp on their outer corners and every
 * seam stays at 5dp, so a group reads as one object without a card border or a divider. A single-row
 * group is both first and last and therefore fully rounded.
 */
fun groupShape(first: Boolean, last: Boolean): Shape =
    RoundedCornerShape(
        topStart = if (first) GROUP_OUTER_RADIUS else GROUP_SEAM_RADIUS,
        topEnd = if (first) GROUP_OUTER_RADIUS else GROUP_SEAM_RADIUS,
        bottomEnd = if (last) GROUP_OUTER_RADIUS else GROUP_SEAM_RADIUS,
        bottomStart = if (last) GROUP_OUTER_RADIUS else GROUP_SEAM_RADIUS,
    )

private val GROUP_OUTER_RADIUS = 18.dp
private val GROUP_SEAM_RADIUS = 5.dp

/** Group heading. Primary-coloured and small: it labels the block without competing with the rows. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.xs, bottom = 5.dp),
    )
}

/** Wraps rows so the 2dp seams are declared once rather than at every call site. */
@Composable
fun GroupedColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

/**
 * One row of a grouped list: icon, title, optional second line, optional current value, chevron.
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = groupShape(first, last),
        modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor ?: MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
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
}
