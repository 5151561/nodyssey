package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.UserNoteStore
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.user_note_dialog_hint
import io.github.nodyssey.ui.resources.user_note_dialog_title
import io.github.nodyssey.ui.resources.user_note_save
import io.github.plaza.designsys.component.TonalTag
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 备注 as every screen sees it: the notes themselves, and a way to open the one editor.
 *
 * A `CompositionLocal` for the reason [io.github.nodyssey.ui.richtext.LocalReportFormat] is one: a
 * name turns up on the feed, in search, on every floor and on a profile, and none of those screens
 * decides anything about notes. Threading a map through five ViewModels would give each a dependency
 * whose only job is to be passed on.
 */
@Immutable
class UserNotes(
    private val notes: Map<Long, String>,
    private val onEdit: (uid: Long, name: String) -> Unit,
) {
    fun noteFor(uid: Long?): String? = uid?.let(notes::get)

    /** Opens the editor for [uid]; [name] is only what the dialog calls them. */
    fun edit(
        uid: Long,
        name: String,
    ) = onEdit(uid, name)
}

val LocalUserNotes = staticCompositionLocalOf { UserNotes(emptyMap()) { _, _ -> } }

/** Provides [LocalUserNotes] from [store] to [content], and hosts the editor dialog they open. */
@Composable
fun ProvideUserNotes(
    store: UserNoteStore,
    content: @Composable () -> Unit,
) {
    val notes by store.notes.collectAsStateWithLifecycle(initialValue = emptyMap())
    // Two saveable primitives rather than one pair, so the open dialog survives a configuration
    // change on every platform's saver without a custom one.
    var editingUid by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val userNotes =
        remember(notes) {
            UserNotes(notes) { uid, name ->
                editingName = name
                editingUid = uid
            }
        }
    CompositionLocalProvider(LocalUserNotes provides userNotes) {
        content()
    }
    editingUid?.let { uid ->
        UserNoteDialog(
            name = editingName,
            initial = notes[uid].orEmpty(),
            onSave = { note ->
                editingUid = null
                scope.launch { store.setNote(uid, note) }
            },
            onDismiss = { editingUid = null },
        )
    }
}

/** The reader's note for [uid] as a tag beside the name, or nothing when there is none. */
@Composable
fun UserNoteTag(
    uid: Long?,
    modifier: Modifier = Modifier,
) {
    val note = LocalUserNotes.current.noteFor(uid) ?: return
    TonalTag(
        text = note,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    )
}

@Composable
private fun UserNoteDialog(
    name: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.user_note_dialog_title, name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(UserNoteStore.MAX_NOTE_LENGTH) },
                placeholder = { Text(stringResource(Res.string.user_note_dialog_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(Res.string.user_note_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
