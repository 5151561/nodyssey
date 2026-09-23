package io.github.plaza.designsys.editor

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.composer_format_bold
import io.github.plaza.designsys.resources.composer_format_code
import io.github.plaza.designsys.resources.composer_format_emoji
import io.github.plaza.designsys.resources.composer_format_heading
import io.github.plaza.designsys.resources.composer_format_image
import io.github.plaza.designsys.resources.composer_format_italic
import io.github.plaza.designsys.resources.composer_format_link
import io.github.plaza.designsys.resources.composer_format_list
import io.github.plaza.designsys.resources.composer_format_mention
import io.github.plaza.designsys.resources.composer_format_quote
import io.github.plaza.designsys.resources.composer_format_strikethrough
import io.github.plaza.designsys.resources.composer_toolbar_customize
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The formatting strip that sits on the keyboard's top edge.
 *
 * Shared by the message bar and the signature field — same component, different action list and
 * different trailing control. The post editor and the reply sheet used to be its other two callers;
 * since "先写，后排版" (boards 1d / 2c) they use [ComposerEditorBar], which keeps the formatting keys
 * on a card that opens on demand instead of on a strip that is always there.
 *
 * Nothing but text-mutating keys goes in here. 预览 and 内容/预览/对照 used to, and they were the
 * reason the keys were 32dp: every slot a view switch took was a slot the keys had to shrink to fit
 * around. Those live in each surface's own chrome now, which is what bought the size below.
 *
 * [keySize] is 48dp — Material's minimum touch target.
 *
 * [active] is what marks the toggled-open panels — the image and emoji keys stay lit while their
 * sheet is showing, which is the only cue that tapping again closes it.
 *
 * [color] and [shape] exist for the signature field, which is the one caller not sitting against the
 * keyboard: inside a settings form the strip has to read as a grouped control rather than as the
 * bottom edge of the screen, and it gets there by being a rounded container instead of a flat one.
 *
 * [onCustomize] adds the wrench that opens the strip's own settings, at the end of the *scrolling*
 * keys: it is the one key nobody reaches for mid-sentence, so it is the right thing to put behind a
 * swipe. [appMenu] sits just ahead of it.
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
                            contentDescription = stringResource(action.label),
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
                            contentDescription = stringResource(Res.string.composer_toolbar_customize),
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
    /** Material's minimum touch target. Every strip uses it. */
    val KeySize = 48.dp
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

val EditorAction.label: StringResource
    get() = when (this) {
        EditorAction.BOLD -> Res.string.composer_format_bold
        EditorAction.ITALIC -> Res.string.composer_format_italic
        EditorAction.STRIKETHROUGH -> Res.string.composer_format_strikethrough
        EditorAction.HEADING -> Res.string.composer_format_heading
        EditorAction.CODE -> Res.string.composer_format_code
        EditorAction.QUOTE -> Res.string.composer_format_quote
        EditorAction.LIST -> Res.string.composer_format_list
        EditorAction.LINK -> Res.string.composer_format_link
        EditorAction.MENTION -> Res.string.composer_format_mention
        EditorAction.IMAGE -> Res.string.composer_format_image
        EditorAction.EMOJI -> Res.string.composer_format_emoji
    }
