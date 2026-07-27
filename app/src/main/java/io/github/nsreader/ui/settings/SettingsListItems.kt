package io.github.nsreader.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nsreader.ui.theme.Spacing

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
    Column(verticalArrangement = Arrangement.spacedBy(GROUP_SEAM), content = content)
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
        shape = expressiveGroupShape(top, bottom),
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
    top: Boolean = false,
    bottom: Boolean = false,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = contentColor,
        shape = expressiveGroupShape(top, bottom),
        modifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        },
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
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val targetStartRadius =
                if (isSelected || index == 0) CONNECTED_OUTER_RADIUS else CONNECTED_INNER_RADIUS
            val targetEndRadius =
                if (isSelected || index == labels.lastIndex) {
                    CONNECTED_OUTER_RADIUS
                } else {
                    CONNECTED_INNER_RADIUS
                }
            val startRadius by animateDpAsState(
                targetValue = targetStartRadius,
                animationSpec = connectedButtonSpring(),
                label = "choice_${index}_start_radius",
            )
            val endRadius by animateDpAsState(
                targetValue = targetEndRadius,
                animationSpec = connectedButtonSpring(),
                label = "choice_${index}_end_radius",
            )
            val containerColor by animateColorAsState(
                targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = connectedButtonSpring(),
                label = "choice_${index}_container",
            )
            val contentColor by animateColorAsState(
                targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = connectedButtonSpring(),
                label = "choice_${index}_content",
            )
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).semantics { this.selected = isSelected },
                shape =
                RoundedCornerShape(
                    topStart = startRadius,
                    bottomStart = startRadius,
                    topEnd = endRadius,
                    bottomEnd = endRadius,
                ),
                color = containerColor,
                contentColor = contentColor,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.width(16.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private val CONNECTED_OUTER_RADIUS = 20.dp
private val CONNECTED_INNER_RADIUS = 5.dp

private fun <T> connectedButtonSpring() =
    spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

/** 2dp: wide enough to read as a seam, narrow enough that the group stays one object. */
private val GROUP_SEAM = 2.dp

internal fun expressiveGroupShape(top: Boolean, bottom: Boolean) =
    RoundedCornerShape(
        topStart = if (top) 18.dp else 5.dp,
        topEnd = if (top) 18.dp else 5.dp,
        bottomEnd = if (bottom) 18.dp else 5.dp,
        bottomStart = if (bottom) 18.dp else 5.dp,
    )
