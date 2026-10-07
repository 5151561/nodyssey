package io.github.plaza.designsys.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.LayerCardShape
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.fadingEndEdge
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.composer_toolbar_customize
import io.github.plaza.designsys.theme.LocalPlazaLayers
import org.jetbrains.compose.resources.stringResource

/**
 * The post and reply editors' bar: one rounded strip on the keyboard's top edge, every key on it.
 *
 * [actions] is the writer's own arrangement (the wrench, [onCustomize], edits it), and the strip
 * scrolls when it is longer than the screen is wide. The keys go first, then [appMenu], then the
 * wrench — the one key nobody reaches for mid-sentence, so it is the right one to put behind a swipe.
 * While there is more of the strip past its end, that edge fades out, which is the only cue a strip
 * cut at a key boundary would otherwise give that it scrolls at all.
 *
 * It replaced "先写，后排版" (boards 1d, 1e, 2c), which kept the formatting keys on a 格式 card that
 * swapped in for the bar, and was taken back on 2026-10-08: one strip, as before.
 */
@Composable
fun ComposerEditorBar(
    actions: List<EditorAction>,
    bodyState: TextFieldState,
    editorState: MarkdownEditorState,
    modifier: Modifier = Modifier,
    /**
     * The strip only, not the emoji panel under it — a host that keeps its text to a readable column
     * narrows the keys with it here. The panel stands in for the keyboard and takes the width a
     * keyboard would, which [modifier] would have narrowed along with the keys.
     */
    barModifier: Modifier = Modifier,
    /** The host owns the photo picker, so [EditorAction.IMAGE] comes back out rather than acting. */
    onPickImages: () -> Unit = {},
    /** Same for [EditorAction.HOSTED_IMAGE]: the host owns the image-host picker too. */
    onPickHostedImages: () -> Unit,
    /** Runs after markup is applied — both editors put focus back in the body with it. */
    onFormatted: () -> Unit = {},
    onCustomize: (() -> Unit)? = null,
    /** Rides at the end of the keys, ahead of the wrench. */
    appMenu: (@Composable () -> Unit)? = null,
    /** Drawn in the keyboard's place while [EditorAction.EMOJI] is lit. */
    emojiPanel: @Composable (EmojiPanelScope) -> Unit = {},
    /** What the body field draws as pictures — the same value it was handed. */
    inlinePictures: InlinePictures?,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // The panel stands in for the keyboard, and back puts it away before it leaves.
    PlazaBackHandler(enabled = editorState.emojiOpen) { editorState.closePanels() }
    val onAction: (EditorAction) -> Unit = { action ->
        editorState.dispatch(action, bodyState, onPickImages, onPickHostedImages, onFormatted) { keyboard?.hide() }
    }

    Column(modifier) {
        Strip(
            actions = actions,
            active = if (editorState.emojiOpen) setOf(EditorAction.EMOJI) else emptySet(),
            onAction = onAction,
            appMenu = appMenu,
            onCustomize = onCustomize,
            modifier = barModifier,
        )
        if (editorState.emojiOpen) emojiPanel(editorState.emojiPanelScope(bodyState, inlinePictures))
    }
}

@Composable
private fun Strip(
    actions: List<EditorAction>,
    active: Set<EditorAction>,
    onAction: (EditorAction) -> Unit,
    appMenu: (@Composable () -> Unit)?,
    onCustomize: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    val scroll = rememberScrollState()
    // Recessed rather than raised: the strip is part of the page the writer is typing on.
    Surface(
        color = layers.inset,
        shape = LayerCardShape,
        modifier = modifier.fillMaxWidth().padding(BarMargin),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadingEndEdge(scroll)
                .horizontalScroll(scroll)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                // 40dp keys, rounded a step inside the strip's own corners; Material still hit-tests
                // them at 48.
                ToolbarKey(
                    icon = action.icon,
                    contentDescription = stringResource(action.label),
                    checkable = action.opensPanel,
                    selected = action in active,
                    onClick = { onAction(action) },
                    size = QuickKeySize,
                    shape = MaterialTheme.shapes.medium,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            appMenu?.invoke()
            onCustomize?.let { customize ->
                IconButton(onClick = customize, modifier = Modifier.size(QuickKeySize)) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = stringResource(Res.string.composer_toolbar_customize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private val BarMargin = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)
private val QuickKeySize = 40.dp
