package io.github.nodyssey.ui.composer

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
import androidx.compose.material3.IconToggleButton
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
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.Spacing

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

/**
 * One key. A panel key is a checkbox; a formatting key is a button.
 *
 * Both branches draw identically — 32dp box, `shapes.medium`, lit state in `surfaceContainer` +
 * `primary`. The split is about semantics: [EditorAction.opensPanel] keys have an on/off state that
 * colour alone conveys, so they go through `IconToggleButton` and TalkBack announces 表情 as
 * "已选中"/"未选中". The formatting keys fire and forget, and dressing them as checkboxes would be a
 * regression, not a fix.
 */
@Composable
private fun ToolbarButton(
    action: EditorAction,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 32dp box, matching the board's density. Both branches take the identical modifier, so the two
    // key flavours measure the same and the strip stays on one grid.
    val modifier = Modifier.size(32.dp)
    val shape = MaterialTheme.shapes.medium
    val icon: @Composable () -> Unit = {
        Icon(
            imageVector = action.icon,
            contentDescription = stringResource(action.labelRes),
            modifier = Modifier.size(20.dp),
        )
    }

    if (action.opensPanel) {
        IconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            modifier = modifier,
            colors = IconButtonDefaults.iconToggleButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                checkedContentColor = MaterialTheme.colorScheme.primary,
            ),
            shape = shape,
            content = icon,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            shape = shape,
            content = icon,
        )
    }
}

private val EditorAction.icon: ImageVector
    get() = when (this) {
        EditorAction.BOLD -> NodysseyIcons.FormatBold
        EditorAction.HEADING -> NodysseyIcons.Title
        EditorAction.CODE -> NodysseyIcons.Code
        EditorAction.QUOTE -> NodysseyIcons.FormatQuote
        EditorAction.LIST -> NodysseyIcons.FormatListBulleted
        EditorAction.LINK -> NodysseyIcons.Link
        EditorAction.MENTION -> NodysseyIcons.AlternateEmail
        EditorAction.IMAGE -> NodysseyIcons.Image
        EditorAction.EMOJI -> NodysseyIcons.Mood
        EditorAction.PREVIEW -> NodysseyIcons.Visibility
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
                // The selectable overload, so TalkBack announces which of the three views is
                // active — colour is the only visual cue and reads as nothing.
                Surface(
                    selected = isSelected,
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
