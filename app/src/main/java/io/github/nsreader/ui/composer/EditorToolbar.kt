package io.github.nsreader.ui.composer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nsreader.R
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.theme.Spacing

/**
 * The formatting strip that sits on the keyboard's top edge.
 *
 * Shared by the post editor (7a/C5) and the reply sheet (6d/C6) — same component, different action
 * list and different trailing control.
 *
 * The trailing control is pinned and the keys scroll under it. Measured on a 360dp phone, seven
 * 32dp keys and a 发布 button fit with a few dp to spare — which means they stop fitting the moment
 * the system font scale goes up, and of the two, a key you have to scroll to is far less costly
 * than a publish button you cannot see.
 *
 * [active] is what marks the toggled-open panels — the image and emoji keys stay lit while their
 * sheet is showing, which is the only cue that tapping again closes it.
 */
@Composable
fun EditorToolbar(
    actions: List<EditorAction>,
    onAction: (EditorAction) -> Unit,
    modifier: Modifier = Modifier,
    active: Set<EditorAction> = emptySet(),
    showDivider: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Box {
            if (showDivider) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        ToolbarButton(
                            action = action,
                            selected = action in active,
                            onClick = { onAction(action) },
                        )
                    }
                }
                trailing()
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    action: EditorAction,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        // 32dp box, 48dp touch target: `IconButton` keeps the minimum interactive size regardless
        // of how small the drawn button is, which is what lets the strip match the board's density
        // without dropping below the accessibility floor.
        modifier = Modifier.size(32.dp),
        colors = if (selected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            )
        } else {
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = stringResource(action.labelRes),
            modifier = Modifier.size(20.dp),
        )
    }
}

private val EditorAction.icon: ImageVector
    get() = when (this) {
        EditorAction.BOLD -> NodeSeekIcons.FormatBold
        EditorAction.HEADING -> NodeSeekIcons.Title
        EditorAction.CODE -> NodeSeekIcons.Code
        EditorAction.QUOTE -> NodeSeekIcons.FormatQuote
        EditorAction.LIST -> NodeSeekIcons.FormatListBulleted
        EditorAction.LINK -> NodeSeekIcons.Link
        EditorAction.MENTION -> NodeSeekIcons.AlternateEmail
        EditorAction.IMAGE -> NodeSeekIcons.Image
        EditorAction.EMOJI -> NodeSeekIcons.Mood
        EditorAction.PREVIEW -> NodeSeekIcons.Visibility
    }

private val EditorAction.labelRes: Int
    get() = when (this) {
        EditorAction.BOLD -> R.string.composer_format_bold
        EditorAction.HEADING -> R.string.composer_format_heading
        EditorAction.CODE -> R.string.composer_format_code
        EditorAction.QUOTE -> R.string.composer_format_quote
        EditorAction.LIST -> R.string.composer_format_list
        EditorAction.LINK -> R.string.composer_format_link
        EditorAction.MENTION -> R.string.composer_format_mention
        EditorAction.IMAGE -> R.string.composer_format_image
        EditorAction.EMOJI -> R.string.composer_format_emoji
        EditorAction.PREVIEW -> R.string.action_preview
    }

/**
 * The 内容 / 预览 / 对照 switch from 7a.
 *
 * A hand-built pill rather than `SingleChoiceSegmentedButtonRow`: the segmented button's own 40dp
 * height and check-mark affordance push the toolbar past the point where the keyboard fits, and the
 * three options here are views of one thing rather than a choice being made.
 */
@Composable
fun <T> ViewModeSwitch(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    onClick = { onSelect(option) },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = Spacing.sm),
                    )
                }
            }
        }
    }
}
