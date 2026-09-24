package io.github.plaza.designsys.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The formatting strip that sits on the keyboard's top edge — the one the signature and 个人介绍 fields
 * write through.
 *
 * Nothing but text-mutating keys goes in here: every slot a view switch takes is a slot the keys have
 * to shrink to fit around, and the keys are Material's 48dp minimum touch target.
 */
@Composable
fun EditorToolbar(
    actions: List<EditorAction>,
    onAction: (EditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Box {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    ToolbarKey(
                        icon = action.icon,
                        contentDescription = stringResource(action.label),
                        // An unlit 表情 key is still a checkbox in the "off" position, while 加粗 is
                        // never a checkbox at all — see [EditorAction.opensPanel].
                        checkable = action.opensPanel,
                        selected = false,
                        onClick = { onAction(action) },
                    )
                }
            }
        }
    }
}

/**
 * One key, on this strip and on [ComposerEditorBar]'s quick bar. A panel key is a checkbox; every
 * other key is a button.
 *
 * Both branches draw identically — same box, same [shape], lit state in [checkedContainerColor] +
 * [checkedContentColor]. The split is about semantics: a [checkable] key has an on/off state that
 * colour alone conveys, so it goes through `IconToggleButton` and TalkBack announces 表情 as
 * "已选中"/"未选中". The formatting keys fire and forget, and dressing them as checkboxes would be a
 * regression, not a fix.
 */
@Composable
internal fun ToolbarKey(
    icon: ImageVector,
    contentDescription: String,
    checkable: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape = MaterialTheme.shapes.medium,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checkedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    checkedContentColor: Color = MaterialTheme.colorScheme.primary,
) {
    // Both branches take the identical modifier, so the two key flavours measure the same and the
    // strip stays on one grid. The glyph is half the box: 24dp inside 48dp is the Material ratio.
    val modifier = Modifier.size(KEY_SIZE)
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(KEY_SIZE / 2),
        )
    }

    if (checkable) {
        IconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            modifier = modifier,
            colors = IconButtonDefaults.iconToggleButtonColors(
                contentColor = contentColor,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            ),
            shape = shape,
            content = content,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
            shape = shape,
            content = content,
        )
    }
}

/** Material's minimum touch target. */
private val KEY_SIZE = 48.dp

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
