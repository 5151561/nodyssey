package io.github.plaza.designsys.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
/**
 * What an emoji panel needs from the editor it hangs under.
 *
 * The panel itself is not shared: its groups are whatever stickers a particular site serves, and the
 * shortcode it inserts is that site's syntax. What *is* shared is the wiring — insert at the caret,
 * backspace, and a recents list that outlives the panel — so the strip passes this to a slot and lets
 * the host supply the panel.
 */
@Stable
class EmojiPanelScope internal constructor(
    val onInsert: (String) -> Unit,
    val onBackspace: () -> Unit,
    val recent: List<String>,
    val onRecentChange: (List<String>) -> Unit,
)

/**
 * What the formatting strip remembers between frames — and, for the emoji recents, between screens.
 *
 * Hoisted out of the strip because both halves outlive it: the panel leaves the composition every time
 * it closes, taking any recents it held with it, and the reply sheet's whole toolbar leaves whenever
 * the sheet is dismissed. Whoever owns the editor owns this, and it is saved rather than remembered so
 * a rotation does not lose the emoji someone just picked.
 */
@Stable
class MarkdownEditorState(
    emojiOpen: Boolean = false,
    recentEmoji: List<String> = emptyList(),
) {
    /** The emoji panel replaces the keyboard rather than stacking on it, so only one is ever true. */
    var emojiOpen by mutableStateOf(emojiOpen)
        private set

    /** Most recently inserted emoji, newest first. */
    var recentEmoji by mutableStateOf(recentEmoji)
        internal set

    internal fun toggleEmoji() {
        emojiOpen = !emojiOpen
    }

    fun closeEmoji() {
        emojiOpen = false
    }

    companion object {
        val Saver: Saver<MarkdownEditorState, Any> =
            listSaver(
                // An ArrayList, not whatever `List` the recents happen to be: the saved-state bundle
                // takes Serializable, and the empty-list singleton is not a reliable one to bet on.
                save = { listOf(it.emojiOpen, ArrayList(it.recentEmoji)) },
                restore = {
                    @Suppress("UNCHECKED_CAST")
                    MarkdownEditorState(it[0] as Boolean, it[1] as List<String>)
                },
            )
    }
}

@Composable
fun rememberMarkdownEditorState(): MarkdownEditorState =
    rememberSaveable(saver = MarkdownEditorState.Saver) { MarkdownEditorState() }

/**
 * The formatting strip, its emoji panel, and the wiring between them and a [TextFieldState].
 *
 * Every editor in the app has the same three-part shape — keys on the keyboard's top edge, the panel
 * they open below, and a text field somewhere above — and used to spell out the same dispatch by hand:
 * toggle the panel, hide the IME, close the panel again on the next formatting key, apply the markup.
 * That is the part worth having once. What each surface still declares for itself is its [actions],
 * whatever [trailing] control sits at the end of the strip, and where [onPickImages] takes the one
 * key that opens something the host owns rather than rewriting text.
 *
 * [content] is emitted between the strip and the panel — the message bar's own input row goes there,
 * so the panel takes the keyboard's place under it rather than pushing it off the screen. The post and
 * reply editors leave it empty, and the two sit flush.
 */
@Composable
fun MarkdownEditorBar(
    actions: List<EditorAction>,
    bodyState: TextFieldState,
    editorState: MarkdownEditorState,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    keySize: Dp = EditorToolbarDefaults.KeySize,
    /** The host owns the photo picker, so [EditorAction.IMAGE] comes back out rather than acting. */
    onPickImages: () -> Unit = {},
    /** Runs after markup is applied — the post editor puts focus back in the body with it. */
    onFormatted: () -> Unit = {},
    /** Adds the wrench at the end of the keys. Only the two surfaces with an arranged strip pass it. */
    onCustomize: (() -> Unit)? = null,
    /** Rides at the end of the keys, ahead of the wrench; see [EditorToolbar]. */
    appMenu: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    /** Drawn in the keyboard's place while [EditorAction.EMOJI] is lit. Empty means no emoji key. */
    emojiPanel: @Composable (EmojiPanelScope) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // The panel stands in for the keyboard, and a keyboard is the one thing back is always expected to
    // dismiss before it leaves the screen.
    BackHandler(enabled = editorState.emojiOpen) { editorState.closeEmoji() }

    Column(modifier) {
        EditorToolbar(
            actions = actions,
            active = if (editorState.emojiOpen) setOf(EditorAction.EMOJI) else emptySet(),
            showDivider = showDivider,
            keySize = keySize,
            onCustomize = onCustomize,
            appMenu = appMenu,
            onAction = { action ->
                when (action) {
                    EditorAction.EMOJI -> {
                        editorState.toggleEmoji()
                        if (editorState.emojiOpen) keyboard?.hide()
                    }

                    EditorAction.IMAGE -> onPickImages()

                    else -> {
                        editorState.closeEmoji()
                        bodyState.edit { applyMarkdown(action) }
                        onFormatted()
                    }
                }
            },
            trailing = trailing,
        )
        content()
        if (editorState.emojiOpen) {
            emojiPanel(
                EmojiPanelScope(
                    onInsert = { text -> bodyState.edit { insertText(text) } },
                    onBackspace = { bodyState.edit { deleteBackwards() } },
                    recent = editorState.recentEmoji,
                    onRecentChange = { editorState.recentEmoji = it },
                ),
            )
        }
    }
}
