package io.github.nsreader.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.Board
import io.github.nsreader.data.composer.PostDraft
import io.github.nsreader.data.composer.PostPermission
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.richtext.RichContent
import io.github.nsreader.ui.theme.PostBody
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth
import java.text.DateFormat
import java.util.Date

@Composable
fun PostComposerRoute(
    viewModel: PostComposerViewModel,
    onClose: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onPublished: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.publishError?.let { publishErrorMessage(it, state.publishErrorDetail) }
    val errorActionLabel = state.publishError?.let { error ->
        stringResource(
            when (error) {
                NodeSeekError.LoginRequired -> R.string.action_sign_in
                NodeSeekError.Cloudflare -> R.string.action_verify
                else -> R.string.action_retry
            },
        )
    }

    LaunchedEffect(state.publishError, errorMessage, errorActionLabel) {
        val error = state.publishError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = errorMessage ?: return@LaunchedEffect,
            actionLabel = errorActionLabel,
            duration = SnackbarDuration.Indefinite,
        )
        viewModel.clearPublishError()
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            when (error) {
                NodeSeekError.LoginRequired -> onSignIn()
                NodeSeekError.Cloudflare -> onVerify()
                else -> viewModel.publish(onPublished)
            }
        }
    }

    PostComposerScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onClose = onClose,
        onBackToEditor = viewModel::showEditor,
        onTitleChange = viewModel::updateTitle,
        onBodyChange = viewModel::updateBody,
        onBoardSelect = viewModel::selectBoard,
        onPermissionSelect = viewModel::selectPermission,
        onPreview = viewModel::showPreview,
        onPublish = {
            if (state.isSignedIn) viewModel.publish(onPublished) else onSignIn()
        },
        onDismissRule = viewModel::dismissRuleReminder,
        onContinueDraft = viewModel::continueDraft,
        onDiscardDraft = viewModel::discardDraft,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerScreen(
    state: PostComposerUiState,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onBackToEditor: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onBoardSelect: (Board) -> Unit,
    onPermissionSelect: (PostPermission) -> Unit,
    onPreview: () -> Unit,
    onPublish: () -> Unit,
    onDismissRule: () -> Unit,
    onContinueDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ComposerTopBar(
                preview = state.mode == ComposerMode.PREVIEW,
                isPublishing = state.isPublishing,
                canPublish = state.canPublish,
                onClose = onClose,
                onBack = onBackToEditor,
                onPublish = onPublish,
            )
        },
    ) { padding ->
        if (state.mode == ComposerMode.EDIT) {
            EditorContent(
                state = state,
                onTitleChange = onTitleChange,
                onBodyChange = onBodyChange,
                onBoardSelect = onBoardSelect,
                onPermissionSelect = onPermissionSelect,
                onPreview = onPreview,
                modifier = Modifier.padding(padding),
            )
        } else {
            PreviewContent(
                state = state,
                onDismissRule = onDismissRule,
                modifier = Modifier.padding(padding),
            )
        }
    }

    state.pendingDraft?.let { draft ->
        DraftRecoveryDialog(
            draft = draft,
            onContinue = onContinueDraft,
            onDiscard = onDiscardDraft,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerTopBar(
    preview: Boolean,
    isPublishing: Boolean,
    canPublish: Boolean,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onPublish: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(if (preview) R.string.composer_preview_title else R.string.composer_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            if (preview) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = Spacing.md)) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
            } else {
                IconButton(onClick = onClose, enabled = !isPublishing) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }
        },
        actions = {
            Button(
                onClick = onPublish,
                enabled = canPublish,
                contentPadding = PaddingValues(horizontal = Spacing.lg),
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text("  ${stringResource(R.string.action_publish)}")
                } else {
                    Text(stringResource(R.string.action_publish))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun EditorContent(
    state: PostComposerUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onBoardSelect: (Board) -> Unit,
    onPermissionSelect: (PostPermission) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bodyValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.body, TextRange(state.body.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.body) {
        if (state.body != bodyValue.text) {
            bodyValue = TextFieldValue(state.body, TextRange(state.body.length))
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        ComposerOptions(
            state = state,
            onBoardSelect = onBoardSelect,
            onPermissionSelect = onPermissionSelect,
        )
        BasicTextField(
            value = state.title,
            onValueChange = onTitleChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .readableWidth()
                .padding(horizontal = Spacing.lg)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = Spacing.sm),
            decorationBox = { inner ->
                Column {
                    Box(Modifier.fillMaxWidth()) {
                        if (state.title.isEmpty()) {
                            Text(
                                stringResource(R.string.composer_title_hint),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(top = Spacing.sm),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(
                            R.string.composer_title_count,
                            state.title.length,
                            PostComposerViewModel.MAX_TITLE_LENGTH,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End).padding(top = Spacing.xs),
                    )
                }
            },
        )
        BasicTextField(
            value = bodyValue,
            onValueChange = {
                bodyValue = it
                onBodyChange(it.text)
            },
            textStyle = PostBody.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .readableWidth()
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize()) {
                    if (bodyValue.text.isEmpty()) {
                        Text(
                            stringResource(R.string.composer_body_hint),
                            style = PostBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        MarkdownToolbar(
            onFormat = { format ->
                bodyValue = applyFormat(bodyValue, format)
                onBodyChange(bodyValue.text)
                focusRequester.requestFocus()
            },
            onPreview = onPreview,
        )
    }
}

@Composable
private fun ComposerOptions(
    state: PostComposerUiState,
    onBoardSelect: (Board) -> Unit,
    onPermissionSelect: (PostPermission) -> Unit,
) {
    var boardMenuOpen by remember { mutableStateOf(false) }
    var permissionMenuOpen by remember { mutableStateOf(false) }
    val boardRequired = state.boardSlug == null && state.hasContent

    Row(
        modifier = Modifier
            .readableWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AssistChip(
                onClick = { boardMenuOpen = true },
                label = { Text(state.boardTitle ?: stringResource(R.string.composer_select_board)) },
                trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp)) },
                modifier = if (boardRequired) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            DropdownMenu(expanded = boardMenuOpen, onDismissRequest = { boardMenuOpen = false }) {
                state.boards.forEach { board ->
                    DropdownMenuItem(
                        text = { Text(board.title) },
                        onClick = {
                            onBoardSelect(board)
                            boardMenuOpen = false
                        },
                    )
                }
            }
        }
        Box {
            AssistChip(
                onClick = { permissionMenuOpen = true },
                label = {
                    Text(
                        stringResource(
                            R.string.composer_permission,
                            permissionLabel(state.permission),
                        ),
                    )
                },
                leadingIcon = {
                    Icon(NodeSeekIcons.Visibility, null, Modifier.size(17.dp))
                },
                trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = permissionMenuOpen, onDismissRequest = { permissionMenuOpen = false }) {
                PostPermission.entries.forEach { permission ->
                    DropdownMenuItem(
                        text = { Text(permissionLabel(permission)) },
                        onClick = {
                            onPermissionSelect(permission)
                            permissionMenuOpen = false
                        },
                    )
                }
            }
        }
        Text(
            text = state.savedAtMillis?.let { stringResource(R.string.composer_draft_saved, formatTime(it)) }
                ?: stringResource(R.string.composer_draft_saving),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun MarkdownToolbar(
    onFormat: (MarkdownFormat) -> Unit,
    onPreview: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatButton("B", R.string.composer_format_bold) { onFormat(MarkdownFormat.BOLD) }
            FormatButton("H2", R.string.composer_format_heading) { onFormat(MarkdownFormat.HEADING) }
            FormatButton("</>", R.string.composer_format_code) { onFormat(MarkdownFormat.CODE) }
            FormatButton("•", R.string.composer_format_list) { onFormat(MarkdownFormat.LIST) }
            FormatButton("↗", R.string.composer_format_link) { onFormat(MarkdownFormat.LINK) }
            FormatButton("▧", R.string.composer_format_image) { onFormat(MarkdownFormat.IMAGE) }
            FormatButton("☺", R.string.composer_format_emoji) { onFormat(MarkdownFormat.EMOJI) }
            TextButton(onClick = onPreview) {
                Icon(NodeSeekIcons.Visibility, null, Modifier.size(18.dp))
                Text("  ${stringResource(R.string.action_preview)}")
            }
        }
    }
}

@Composable
private fun FormatButton(
    label: String,
    descriptionRes: Int,
    onClick: () -> Unit,
) {
    val description = stringResource(descriptionRes)
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PreviewContent(
    state: PostComposerUiState,
    onDismissRule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val nodes = remember(state.body) { parseMarkdown(state.body) }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize().readableWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.showRuleReminder) {
            item(key = "rule-reminder") {
                RuleReminder(onDismissRule)
            }
        }
        item(key = "title") {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item(key = "meta") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        state.boardTitle.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
                Text(
                    stringResource(R.string.composer_just_now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "body") {
            if (nodes.isEmpty()) {
                Text(
                    stringResource(R.string.composer_preview_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                RichContent(
                    nodes = nodes,
                    onLinkClick = { url ->
                        if (NodeSeekSite.isExternalWebUrl(url)) runCatching { uriHandler.openUri(url) }
                    },
                    onImageClick = { url ->
                        if (NodeSeekSite.isExternalWebUrl(url)) runCatching { uriHandler.openUri(url) }
                    },
                )
            }
        }
    }
}

@Composable
private fun RuleReminder(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Info, null, Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                Text(stringResource(R.string.composer_rule_title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.composer_rule_body), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.composer_rule_dismiss))
            }
        }
    }
}

@Composable
private fun DraftRecoveryDialog(
    draft: PostDraft,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.MailOutline, null) },
        title = { Text(stringResource(R.string.composer_restore_title)) },
        text = {
            Text(
                stringResource(
                    R.string.composer_restore_body,
                    formatTime(draft.savedAtMillis),
                    draft.boardTitle ?: stringResource(R.string.composer_select_board),
                    draft.title.ifBlank { stringResource(R.string.composer_title_hint) },
                ),
            )
        },
        confirmButton = {
            Button(onClick = onContinue) { Text(stringResource(R.string.composer_restore_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.composer_restore_discard)) }
        },
    )
}

@Composable
private fun publishErrorMessage(error: NodeSeekError, detail: String?): String {
    val reason = when (error) {
        NodeSeekError.Network -> stringResource(R.string.composer_publish_network_failed)

        NodeSeekError.LoginRequired -> stringResource(R.string.composer_publish_login_required)

        NodeSeekError.Cloudflare -> stringResource(R.string.composer_publish_challenge)

        is NodeSeekError.Http -> {
            val status = stringResource(R.string.composer_publish_http, error.statusCode)
            detail?.takeIf { it.isNotBlank() && it != error.toString() }?.let { "$status：$it" } ?: status
        }

        else -> detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.composer_publish_unknown)
    }
    return stringResource(R.string.composer_publish_failed, reason)
}

private enum class MarkdownFormat { BOLD, HEADING, CODE, LIST, LINK, IMAGE, EMOJI }

private fun applyFormat(value: TextFieldValue, format: MarkdownFormat): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(0, value.text.length)
    val selected = value.text.substring(start, end)
    val (replacement, cursorOffset) = when (format) {
        MarkdownFormat.BOLD -> "**${selected.ifEmpty { "加粗文字" }}**" to if (selected.isEmpty()) 2 else selected.length + 4
        MarkdownFormat.HEADING -> "## ${selected.ifEmpty { "标题" }}" to if (selected.isEmpty()) 3 else selected.length + 3
        MarkdownFormat.CODE -> "`${selected.ifEmpty { "code" }}`" to if (selected.isEmpty()) 1 else selected.length + 2
        MarkdownFormat.LIST -> "- ${selected.ifEmpty { "列表项" }}" to if (selected.isEmpty()) 2 else selected.length + 2
        MarkdownFormat.LINK -> "[${selected.ifEmpty { "链接文字" }}](https://)" to if (selected.isEmpty()) 1 else selected.length + 3
        MarkdownFormat.IMAGE -> "![${selected.ifEmpty { "图片说明" }}](https://)" to if (selected.isEmpty()) 2 else selected.length + 4
        MarkdownFormat.EMOJI -> "$selected🙂" to selected.length + 2
    }
    val text = value.text.replaceRange(start, end, replacement)
    val cursor = (start + cursorOffset).coerceIn(0, text.length)
    return TextFieldValue(text, TextRange(cursor))
}

private fun formatTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

@Composable
private fun permissionLabel(permission: PostPermission): String =
    stringResource(
        when (permission) {
            PostPermission.PUBLIC -> R.string.composer_permission_public
            PostPermission.LEVEL_ONE -> R.string.composer_permission_level_one
            PostPermission.PRIVATE -> R.string.composer_permission_private
        },
    )
