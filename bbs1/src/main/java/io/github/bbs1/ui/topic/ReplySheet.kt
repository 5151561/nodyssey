package io.github.bbs1.ui.topic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bbs1.R
import io.github.bbs1.ui.common.apiErrorText
import io.github.bbs1.ui.composer.Bbs1EditorActions
import io.github.plaza.designsys.component.EditorTextField
import io.github.plaza.designsys.editor.MarkdownEditorBar
import io.github.plaza.designsys.editor.rememberMarkdownEditorState
import io.github.plaza.designsys.theme.Spacing

/**
 * The reply editor, as a sheet over the thread.
 *
 * The draft lives in [bodyState], which the thread screen owns: a dismissed sheet leaves the
 * composition, and a draft that died with it would be a sheet that eats what someone typed every time
 * they closed it to check what they were replying to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplySheet(
    state: TopicUiState,
    bodyState: TextFieldState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    // Expanded or gone, with no half-height stop: the editor's own height is what it needs, and a
    // partially expanded sheet would cut the toolbar off the bottom of it.
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val editorState = rememberMarkdownEditorState()
    val focusRequester = remember { FocusRequester() }
    val canSend by remember { derivedStateOf { bodyState.text.isNotBlank() } }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // A bare `imePadding` on purpose: the sheet gets no Scaffold padding to consume, and adding
        // the navigation bar on top of the keyboard's height would float the toolbar above it.
        Column(Modifier.imePadding()) {
            EditorTextField(
                state = bodyState,
                hint = stringResource(R.string.bbs1_reply_hint),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .focusRequester(focusRequester),
            )

            if (state.replyError != null) {
                Text(
                    text = apiErrorText(state.replyError),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }

            MarkdownEditorBar(
                actions = Bbs1EditorActions,
                bodyState = bodyState,
                editorState = editorState,
                trailing = {
                    Box(Modifier.padding(end = Spacing.sm)) {
                        if (state.replySubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(Spacing.md).size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = { onSubmit(bodyState.text.toString()) },
                                enabled = canSend,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.bbs1_action_send_reply),
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}
