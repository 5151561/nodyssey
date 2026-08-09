package io.github.bbs1.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bbs1.R
import io.github.bbs1.ui.common.apiErrorText
import io.github.plaza.designsys.component.EditorTextField
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView
import io.github.plaza.designsys.editor.MarkdownEditorBar
import io.github.plaza.designsys.editor.rememberMarkdownEditorState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard

/** A new thread: pick a board, write a title and a body, publish. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeTopicScreen(
    state: ComposeTopicUiState,
    onClose: () -> Unit,
    onSelectForum: (Long) -> Unit,
    onSubmit: (title: String, body: String) -> Unit,
    onCreated: (Long) -> Unit,
    onRetryForums: () -> Unit,
) {
    val title = rememberTextFieldState()
    val body = rememberTextFieldState()
    val editorState = rememberMarkdownEditorState()

    LaunchedEffect(state.createdTopicId) {
        state.createdTopicId?.let(onCreated)
    }

    // Derived so typing recomposes the app bar only when the answer flips, not on every keystroke.
    val ready by remember { derivedStateOf { title.text.isNotBlank() && body.text.isNotBlank() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bbs1_compose_topic_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.bbs1_action_cancel),
                        )
                    }
                },
                actions = {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = Spacing.lg).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = { onSubmit(title.text.toString(), body.text.toString()) },
                            enabled = ready && state.selectedForumId != null,
                        ) {
                            Text(stringResource(R.string.bbs1_action_publish))
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Not a `bottomBar`: the formatting strip belongs on top of the keyboard, and a bottom bar is
        // laid out against the window, which leaves it under the navigation bar with the keyboard up.
        // `paddingWithKeyboard` is the shared answer to that; see its comment.
        Box(Modifier.fillMaxSize().paddingWithKeyboard(padding)) {
            when {
                state.loading -> LoadingState()

                state.forums.isEmpty() ->
                    StatusView(
                        icon = Icons.Default.Warning,
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        iconColor = MaterialTheme.colorScheme.onErrorContainer,
                        title = stringResource(R.string.bbs1_compose_no_forum_title),
                        description =
                        state.error?.let { apiErrorText(it) }
                            ?: stringResource(R.string.bbs1_compose_no_forum_body),
                        primaryAction = StatusAction(stringResource(R.string.bbs1_action_retry), onRetryForums),
                    )

                else ->
                    Column(Modifier.fillMaxSize()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            items(state.forums, key = { it.id }) { forum ->
                                FilterChip(
                                    selected = state.selectedForumId == forum.id,
                                    onClick = { onSelectForum(forum.id) },
                                    label = { Text(forum.name) },
                                )
                            }
                        }

                        EditorTextField(
                            state = title,
                            hint = stringResource(R.string.bbs1_compose_title_hint),
                            textStyle = MaterialTheme.typography.titleLarge,
                            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        )
                        HorizontalDivider(Modifier.padding(horizontal = Spacing.lg))

                        if (state.error != null) {
                            Text(
                                text = apiErrorText(state.error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            )
                        }

                        EditorTextField(
                            state = body,
                            hint = stringResource(R.string.bbs1_compose_body_hint),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        )

                        MarkdownEditorBar(
                            actions = Bbs1EditorActions,
                            bodyState = body,
                            editorState = editorState,
                        )
                    }
            }
        }
    }
}
