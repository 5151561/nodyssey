package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * What an emoji panel needs from the editor it hangs under.
 *
 * The panel itself is not shared: its groups are whatever stickers a particular site serves, and the
 * shortcode it inserts is that site's syntax. What *is* shared is the wiring — insert at the caret,
 * backspace, and a recents list that outlives the panel — so the editor passes this to a slot and lets
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
 * What an editor's panels remember between frames — and, for the emoji recents, between screens.
 *
 * Hoisted out of the bar because both halves outlive it: the panel leaves the composition every time
 * it closes, taking any recents it held with it, and the reply sheet's whole toolbar leaves whenever
 * the sheet is dismissed. Whoever owns the editor owns this, and it is saved rather than remembered so
 * a rotation does not lose the emoji someone just picked.
 */
@Stable
class MarkdownEditorState(
    emojiOpen: Boolean = false,
    recentEmoji: List<String> = emptyList(),
    formatOpen: Boolean = false,
) {
    /** The emoji panel replaces the keyboard rather than stacking on it, so only one is ever true. */
    var emojiOpen by mutableStateOf(emojiOpen)
        private set

    /**
     * Whether the editor's card of formatting keys is open in the keyboard's place — [ComposerEditorBar]'s
     * 格式 card, the message bar's tool grid.
     *
     * Hoisted with the rest for the same reason: a rotation mid-edit should come back to the card the
     * writer had open.
     */
    var formatOpen by mutableStateOf(formatOpen)
        private set

    /** Most recently inserted emoji, newest first. */
    var recentEmoji by mutableStateOf(recentEmoji)
        internal set

    internal fun toggleEmoji() {
        emojiOpen = !emojiOpen
        // The two panels are alternatives: a sticker grid under a format card is two drawers open at
        // once over a field the writer can no longer see.
        if (emojiOpen) formatOpen = false
    }

    fun toggleFormat() {
        formatOpen = !formatOpen
        if (formatOpen) emojiOpen = false
    }

    internal fun closeEmoji() {
        emojiOpen = false
    }

    /** Puts away whichever panel is open — what back does, and what dismissing a sheet does. */
    fun closePanels() {
        emojiOpen = false
        formatOpen = false
    }

    companion object {
        val Saver: Saver<MarkdownEditorState, Any> =
            listSaver(
                // An ArrayList, not whatever `List` the recents happen to be: the saved-state bundle
                // takes Serializable, and the empty-list singleton is not a reliable one to bet on.
                save = { listOf(it.emojiOpen, ArrayList(it.recentEmoji), it.formatOpen) },
                restore = {
                    @Suppress("UNCHECKED_CAST")
                    MarkdownEditorState(it[0] as Boolean, it[1] as List<String>, it[2] as Boolean)
                },
            )
    }
}

@Composable
fun rememberMarkdownEditorState(): MarkdownEditorState =
    rememberSaveable(saver = MarkdownEditorState.Saver) { MarkdownEditorState() }

/**
 * What a key does, for every editor: toggle the panel, hand the picker back, or rewrite the text.
 *
 * [hideKeyboard] runs only when the emoji panel has just opened — the panel takes the keyboard's
 * place, and the two stacked leave two lines of the text visible.
 */
fun MarkdownEditorState.dispatch(
    action: EditorAction,
    bodyState: TextFieldState,
    onPickImages: () -> Unit,
    onFormatted: () -> Unit,
    hideKeyboard: () -> Unit,
) {
    when (action) {
        EditorAction.EMOJI -> {
            toggleEmoji()
            if (emojiOpen) hideKeyboard()
        }

        EditorAction.IMAGE -> onPickImages()

        else -> {
            closeEmoji()
            bodyState.edit { applyMarkdown(action) }
            onFormatted()
        }
    }
}

/** The emoji panel's wiring into [bodyState] and these recents. */
fun MarkdownEditorState.emojiPanelScope(bodyState: TextFieldState) =
    EmojiPanelScope(
        onInsert = { text -> bodyState.edit { insertText(text) } },
        onBackspace = { bodyState.edit { deleteBackwards() } },
        recent = recentEmoji,
        onRecentChange = { recentEmoji = it },
    )
