package io.github.plaza.designsys.editor

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plaza.designsys.R
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.Spacing

/**
 * The formatting strip that sits on the keyboard's top edge.
 *
 * Shared by the post editor (7a/C5), the reply sheet (6d/C6), the message bar and the signature field
 * — same component, different action list and different trailing control.
 *
 * Nothing but text-mutating keys goes in here. 预览 and 内容/预览/对照 used to, and they were the
 * reason the keys were 32dp: every slot a view switch took was a slot the keys had to shrink to fit
 * around. Those live in each surface's own chrome now, which is what bought the size below.
 *
 * [keySize] is 48dp — Material's minimum touch target — for every caller with room for it. The reply
 * sheet is the one that has not: it keeps 发布 pinned at the end of the strip, and six 48dp keys plus
 * that button want 392dp on a 360dp screen. It passes 42dp and stays scroll-free, which was the
 * deliberate trade over the alternative, a strip that scrolls its last key under the publish button.
 *
 * [active] is what marks the toggled-open panels — the image and emoji keys stay lit while their
 * sheet is showing, which is the only cue that tapping again closes it.
 *
 * [color] and [shape] exist for the signature field, which is the one caller not sitting against the
 * keyboard: inside a settings form the strip has to read as a grouped control rather than as the
 * bottom edge of the screen, and it gets there by being a rounded container instead of a flat one.
 *
 * [onCustomize] adds the wrench that opens the strip's own settings — only the two composers whose
 * strip is user-arranged pass it. It rides at the end of the *scrolling* keys rather than pinned
 * beside [trailing]: it is the one key nobody reaches for mid-sentence, so it is the right thing to
 * put behind a swipe, and the reply sheet has exactly one pinned slot and 发布 has it.
 *
 * [appMenu] sits just ahead of the wrench, and for the second half of that reason rather than the
 * first: the things it opens — a poll, a 收款码 — are worth reaching for, but the reply sheet's one
 * pinned slot is spoken for, and putting the menu there in the post editor and behind a swipe in the
 * reply sheet would mean the same control lived in two different places.
 */
@Composable
fun EditorToolbar(
    actions: List<EditorAction>,
    onAction: (EditorAction) -> Unit,
    modifier: Modifier = Modifier,
    active: Set<EditorAction> = emptySet(),
    showDivider: Boolean = true,
    keySize: Dp = EditorToolbarDefaults.KeySize,
    color: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = RectangleShape,
    onCustomize: (() -> Unit)? = null,
    appMenu: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = color, shape = shape, modifier = modifier) {
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
                        ToolbarKey(
                            icon = action.icon,
                            contentDescription = stringResource(action.labelRes),
                            size = keySize,
                            // An unlit 表情 key is still a checkbox in the "off" position, while 加粗
                            // is never a checkbox at all — see [EditorAction.opensPanel].
                            checkable = action.opensPanel,
                            selected = action in active,
                            onClick = { onAction(action) },
                        )
                    }
                    appMenu?.invoke()
                    onCustomize?.let { customize ->
                        ToolbarKey(
                            icon = PlazaIcons.Build,
                            contentDescription = stringResource(R.string.composer_toolbar_customize),
                            size = keySize,
                            checkable = false,
                            selected = false,
                            onClick = customize,
                        )
                    }
                }
                trailing()
            }
        }
    }
}

/**
 * One key. A panel key is a checkbox; every other key is a button.
 *
 * Both branches draw identically — same box, `shapes.medium`, lit state in `surfaceContainer` +
 * `primary`. The split is about semantics: a [checkable] key has an on/off state that colour alone
 * conveys, so it goes through `IconToggleButton` and TalkBack announces 表情 as "已选中"/"未选中".
 * The formatting keys and the wrench fire and forget, and dressing them as checkboxes would be a
 * regression, not a fix.
 */
@Composable
private fun ToolbarKey(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    checkable: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Both branches take the identical modifier, so the two key flavours measure the same and the
    // strip stays on one grid. The glyph is half the box: 24dp inside 48dp is the Material ratio, and
    // scaling it with the box is what keeps the tighter reply strip from looking like a different set.
    val modifier = Modifier.size(size)
    val shape = MaterialTheme.shapes.medium
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size / 2),
        )
    }

    if (checkable) {
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
            content = content,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            shape = shape,
            content = content,
        )
    }
}

object EditorToolbarDefaults {
    /** Material's minimum touch target. Every strip with the room for it uses this. */
    val KeySize = 48.dp

    /**
     * What the reply sheet passes: six keys and a pinned 发布 in 360dp, without a scroll.
     *
     * Below the 48dp minimum on purpose, and the only place in the app that is. The alternative was a
     * strip that scrolls, and a scrolling strip always hides its *last* key under the publish button —
     * so the cost lands on one specific key rather than being spread as 6dp across all six.
     */
    val CompactKeySize = 42.dp
}

val EditorAction.icon: ImageVector
    get() = when (this) {
        EditorAction.BOLD -> PlazaIcons.FormatBold
        EditorAction.ITALIC -> PlazaIcons.FormatItalic
        EditorAction.STRIKETHROUGH -> PlazaIcons.StrikethroughS
        EditorAction.HEADING -> PlazaIcons.Title
        EditorAction.CODE -> PlazaIcons.Code
        EditorAction.QUOTE -> PlazaIcons.FormatQuote
        EditorAction.LIST -> PlazaIcons.FormatListBulleted
        EditorAction.LINK -> PlazaIcons.Link
        EditorAction.MENTION -> PlazaIcons.AlternateEmail
        EditorAction.IMAGE -> PlazaIcons.Image
        EditorAction.EMOJI -> PlazaIcons.Mood
    }

val EditorAction.labelRes: Int
    get() = when (this) {
        EditorAction.BOLD -> R.string.composer_format_bold
        EditorAction.ITALIC -> R.string.composer_format_italic
        EditorAction.STRIKETHROUGH -> R.string.composer_format_strikethrough
        EditorAction.HEADING -> R.string.composer_format_heading
        EditorAction.CODE -> R.string.composer_format_code
        EditorAction.QUOTE -> R.string.composer_format_quote
        EditorAction.LIST -> R.string.composer_format_list
        EditorAction.LINK -> R.string.composer_format_link
        EditorAction.MENTION -> R.string.composer_format_mention
        EditorAction.IMAGE -> R.string.composer_format_image
        EditorAction.EMOJI -> R.string.composer_format_emoji
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
