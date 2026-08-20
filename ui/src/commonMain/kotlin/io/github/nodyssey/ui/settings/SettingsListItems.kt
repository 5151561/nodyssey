package io.github.nodyssey.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.GroupSeam
import io.github.plaza.designsys.component.groupShape
import io.github.plaza.designsys.theme.Spacing

/**
 * The grouped-list vocabulary both settings screens are built from.
 *
 * Shared rather than copied because the corner rhythm is the point: 18dp on a group's outer corners,
 * 5dp on the inner ones, 2dp of gap between rows. That is what makes a group read as one object
 * instead of a stack of cards, and two screens rounding their groups differently is exactly the kind
 * of drift nobody notices in review and everybody notices side by side.
 */
@Composable
internal fun SettingsSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.xs, top = Spacing.xs).semantics { heading() },
    )
}

@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupSeam), content = content)
}

/** A row whose control needs its own line — a slider, a segmented button, a preview block. */
@Composable
internal fun SettingsBlock(
    title: String,
    top: Boolean = false,
    bottom: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = groupShape(first = top, last = bottom),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.invoke()
                Column(
                    modifier = Modifier.weight(1f).padding(start = if (icon == null) 0.dp else Spacing.md),
                ) {
                    Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
    }
}

/**
 * A single-line row: leading icon, title, optional current value, optional trailing control.
 *
 * [contentColor] exists for the one destructive row in the family (退出登录), which is the same shape
 * as its neighbours and differs only in colour — a separate composable for it would duplicate the
 * layout to change two tints.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    top: Boolean = false,
    bottom: Boolean = false,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = contentColor,
        shape = groupShape(first = top, last = bottom),
        modifier = modifier.then(
            when {
                checked != null && onCheckedChange != null ->
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )

                onClick != null -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)

                else -> Modifier
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
        }
    }
}

/**
 * The M3E connected button group: one full-round selected segment with a check, 5dp seams between
 * the rest. Shared by the theme picker and the poll-frequency picker so the two cannot drift.
 */
@Composable
internal fun ConnectedChoiceButtons(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
