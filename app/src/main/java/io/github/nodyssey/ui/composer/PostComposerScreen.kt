package io.github.nodyssey.ui.composer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.PostDraft
import io.github.nodyssey.data.composer.PostPermission
import io.github.nodyssey.data.composer.UploadFailure
import io.github.nodyssey.ui.common.EditorTextField
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.PostBody
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.paddingWithKeyboard
import io.github.nodyssey.ui.theme.readableWidth
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    PublishErrorSnackbar(
        state = state,
        snackbarHostState = snackbarHostState,
        onDismissed = viewModel::clearPublishError,
        onSignIn = onSignIn,
        onVerify = onVerify,
        onRetry = { viewModel.publish(onPublished) },
    )
    UploadErrorSnackbar(
        failedCount = state.failedUploadCount,
        failure = state.uploadFailure,
        detail = state.uploadErrorDetail,
        snackbarHostState = snackbarHostState,
        onRetry = viewModel::retryFailedUploads,
    )

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_PICK),
    ) { uris -> viewModel.addImages(uris.toPickedImages(context)) }

    PostComposerScreen(
        state = state,
        titleState = viewModel.titleState,
        bodyState = viewModel.bodyState,
        snackbarHostState = snackbarHostState,
        onClose = onClose,
        onBoardSelect = viewModel::selectBoard,
        onPermissionSelect = viewModel::selectPermission,
        onViewModeChange = viewModel::setViewMode,
        onPickImages = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemoveAttachment = viewModel::removeAttachment,
        onRetryAttachment = viewModel::retryUpload,
        onPublish = { if (state.isSignedIn) viewModel.publish(onPublished) else onSignIn() },
        onContinueDraft = viewModel::continueDraft,
        onDiscardDraft = viewModel::discardDraft,
        onToolbarChange = viewModel::setToolbar,
        onToolbarReset = viewModel::resetToolbar,
        modifier = modifier,
    )
}

@Composable
private fun PublishErrorSnackbar(
    state: PostComposerUiState,
    snackbarHostState: SnackbarHostState,
    onDismissed: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onRetry: () -> Unit,
) {
    val message = state.publishError?.let { publishErrorMessage(it, state.publishErrorDetail) }
    val actionLabel = state.publishError?.let { error ->
        stringResource(
            when (error) {
                NodeSeekError.LoginRequired -> R.string.action_sign_in
                NodeSeekError.Cloudflare -> R.string.action_verify
                else -> R.string.action_retry
            },
        )
    }
    LaunchedEffect(state.publishError, message, actionLabel) {
        val error = state.publishError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message ?: return@LaunchedEffect,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Indefinite,
        )
        onDismissed()
        if (result == SnackbarResult.ActionPerformed) {
            when (error) {
                NodeSeekError.LoginRequired -> onSignIn()
                NodeSeekError.Cloudflare -> onVerify()
                else -> onRetry()
            }
        }
    }
}

@Composable
private fun UploadErrorSnackbar(
    failedCount: Int,
    failure: UploadFailure?,
    detail: String?,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
) {
    val message = uploadFailureText(failedCount, failure, detail)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(failedCount, message) {
        if (failedCount == 0) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = retry,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onRetry()
    }
}

@Composable
fun PostComposerScreen(
    state: PostComposerUiState,
    titleState: TextFieldState,
    bodyState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onBoardSelect: (Board) -> Unit,
    onPermissionSelect: (PostPermission) -> Unit,
    onViewModeChange: (ComposerViewMode) -> Unit,
    onPickImages: () -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onPublish: () -> Unit,
    onContinueDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    onToolbarChange: (List<EditorAction>) -> Unit,
    onToolbarReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // While a publish is in flight the request may already have created the topic; leaving now would
    // cancel the ViewModel, keep the draft, and set up a duplicate post on the next attempt. Preview
    // reuses the same handler so system back mirrors the top bar's arrow instead of closing.
    BackHandler(enabled = state.isPublishing || state.viewMode == ComposerViewMode.PREVIEW) {
        if (!state.isPublishing) onViewModeChange(ComposerViewMode.CONTENT)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ComposerTopBar(
                viewMode = state.viewMode,
                isPublishing = state.isPublishing,
                canPublish = state.canPublish,
                onClose = onClose,
                onViewModeChange = onViewModeChange,
                onPublish = onPublish,
            )
        },
    ) { padding ->
        if (state.viewMode == ComposerViewMode.PREVIEW) {
            PreviewContent(state = state, modifier = Modifier.padding(padding))
        } else {
            EditorContent(
                state = state,
                titleState = titleState,
                bodyState = bodyState,
                onBoardSelect = onBoardSelect,
                onPermissionSelect = onPermissionSelect,
                onPickImages = onPickImages,
                onRemoveAttachment = onRemoveAttachment,
                onRetryAttachment = onRetryAttachment,
                onToolbarChange = onToolbarChange,
                onToolbarReset = onToolbarReset,
                modifier = Modifier.paddingWithKeyboard(padding),
            )
        }
    }

    state.pendingDraft?.let { draft ->
        DraftRecoveryDialog(draft = draft, onContinue = onContinueDraft, onDiscard = onDiscardDraft)
    }
}

/**
 * Close, the view switch, publish.
 *
 * The switch is here rather than in the formatting strip because 内容/预览/对照 is not a formatting
 * action — it changes what the whole screen shows, the way the close and publish buttons beside it do.
 * It sat in the toolbar's trailing slot until users called the keys "蚂蚁一样": three of the strip's
 * seven slots went to a view switch, which is what forced the remaining keys down to 32dp.
 *
 * It also replaces the title. "发布帖子" told you where you were on a screen that could not be
 * anywhere else, and the switch says the same thing better by showing which of the three views is
 * live. With the switch always reachable there is no back arrow either — tapping 内容 is the way out
 * of a preview, and 关闭 stays 关闭 in every mode instead of turning into something else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerTopBar(
    viewMode: ComposerViewMode,
    isPublishing: Boolean,
    canPublish: Boolean,
    onClose: () -> Unit,
    onViewModeChange: (ComposerViewMode) -> Unit,
    onPublish: () -> Unit,
) {
    TopAppBar(
        title = {
            ViewModeSwitch(
                options = ComposerViewMode.entries,
                selected = viewMode,
                label = { mode -> stringResource(mode.labelRes) },
                onSelect = onViewModeChange,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isPublishing) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                )
            }
        },
        actions = { PublishButton(isPublishing = isPublishing, enabled = canPublish, onClick = onPublish) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/**
 * "发布" and its in-flight twin from 7c.
 *
 * The tonal, spinner-carrying variant replaces the filled button rather than showing a spinner
 * inside it: the difference has to survive being glanced at, because the one thing a user must not
 * do here is tap again.
 */
@Composable
private fun PublishButton(
    isPublishing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isPublishing,
        contentPadding = PaddingValues(horizontal = 18.dp),
        colors = if (isPublishing) {
            ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = Modifier.padding(end = Spacing.sm).height(40.dp),
    ) {
        if (isPublishing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.composer_publishing),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        } else {
            Text(stringResource(R.string.action_publish), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EditorContent(
    state: PostComposerUiState,
    titleState: TextFieldState,
    bodyState: TextFieldState,
    onBoardSelect: (Board) -> Unit,
    onPermissionSelect: (PostPermission) -> Unit,
    onPickImages: () -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onToolbarChange: (List<EditorAction>) -> Unit,
    onToolbarReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorState = rememberMarkdownEditorState()
    val focusRequester = remember { FocusRequester() }
    var customizing by rememberSaveable { mutableStateOf(false) }

    // The keyboard padding is the caller's — [paddingWithKeyboard] has to sit next to the Scaffold
    // padding it consumes, and applying `imePadding` again here would put the gap right back.
    Column(modifier = modifier.fillMaxSize()) {
        ComposerOptions(state = state, onBoardSelect = onBoardSelect, onPermissionSelect = onPermissionSelect)
        TitleField(titleState = titleState, length = state.title.length)
        BodyArea(
            state = state,
            bodyState = bodyState,
            focusRequester = focusRequester,
            modifier = Modifier.weight(1f),
        )
        AttachmentTray(
            attachments = state.attachments,
            onRemove = onRemoveAttachment,
            onRetry = onRetryAttachment,
        )
        MarkdownEditorBar(
            actions = state.toolbar.enabled,
            bodyState = bodyState,
            editorState = editorState,
            onPickImages = onPickImages,
            onCustomize = { customizing = true },
            // The toolbar takes focus when it is tapped, and a caret the user cannot see is a caret
            // they have lost track of.
            onFormatted = { focusRequester.requestFocus() },
        )
    }

    if (customizing) {
        ToolbarCustomizeSheet(
            layout = state.toolbar,
            onChange = onToolbarChange,
            onReset = onToolbarReset,
            onDismiss = { customizing = false },
        )
    }
}

@Composable
private fun BodyArea(
    state: PostComposerUiState,
    bodyState: TextFieldState,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (state.viewMode != ComposerViewMode.COMPARE) {
        BodyField(bodyState, focusRequester, modifier)
        return
    }
    // 对照: the site puts the two side by side, which needs a width a phone does not have. Stacked
    // keeps the pairing — edit above, result below — without shrinking either to an unreadable column.
    Column(modifier = modifier) {
        BodyField(bodyState, focusRequester, Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MarkdownPreviewBody(
            markdown = state.body,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
}

@Composable
private fun BodyField(
    bodyState: TextFieldState,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    EditorTextField(
        state = bodyState,
        hint = stringResource(R.string.composer_body_hint),
        textStyle = PostBody.copy(color = MaterialTheme.colorScheme.onSurface),
        hintStyle = PostBody,
        modifier = modifier
            .readableWidth()
            .focusRequester(focusRequester)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        // The body owns the rest of the screen, so the placeholder is drawn against all of it rather
        // than against one line's worth.
        container = { content -> Box(Modifier.fillMaxSize()) { content() } },
    )
}

@Composable
private fun TitleField(
    titleState: TextFieldState,
    length: Int,
) {
    EditorTextField(
        state = titleState,
        hint = stringResource(R.string.composer_title_hint),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = 19.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        // The placeholder is not bold: the weight belongs to a title that exists.
        hintStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
        lineLimits = TextFieldLineLimits.SingleLine,
        // Enforced at the input layer rather than trimmed afterwards: truncating in the ViewModel
        // cut composing text out from under the IME, which made the field stutter mid-word.
        inputTransformation = InputTransformation.maxLength(PostComposerViewModel.MAX_TITLE_LENGTH),
        modifier = Modifier.readableWidth().padding(horizontal = Spacing.lg),
        container = { content ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f).padding(bottom = Spacing.sm)) { content() }
                    Text(
                        text = stringResource(
                            R.string.composer_title_count,
                            length,
                            PostComposerViewModel.MAX_TITLE_LENGTH,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.md),
                    )
                }
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Composable
private fun ComposerOptions(
    state: PostComposerUiState,
    onBoardSelect: (Board) -> Unit,
    onPermissionSelect: (PostPermission) -> Unit,
) {
    var boardMenuOpen by remember { mutableStateOf(false) }
    var permissionMenuOpen by remember { mutableStateOf(false) }
    // The board is the one required field with no default, so it is called out only once there is
    // something to publish — an error outline on an untouched form is just noise.
    val boardMissing = state.boardSlug == null && state.hasContent

    Row(
        modifier = Modifier
            .readableWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ComposerChip(
                label = state.boardTitle ?: stringResource(R.string.composer_select_board),
                filled = state.boardTitle != null,
                error = boardMissing,
                onClick = { boardMenuOpen = true },
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
            ComposerChip(
                label = permissionLabel(state.permission),
                filled = false,
                error = false,
                leading = NodysseyIcons.Visibility,
                onClick = { permissionMenuOpen = true },
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
        Box(Modifier.weight(1f))
        Text(
            text = state.savedAtMillis?.let { stringResource(R.string.composer_draft_saved, formatTime(it)) }
                ?: stringResource(R.string.composer_draft_saving),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ComposerChip(
    label: String,
    filled: Boolean,
    error: Boolean,
    onClick: () -> Unit,
    leading: ImageVector? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (filled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (filled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            leading?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PreviewContent(
    state: PostComposerUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth(),
    ) {
        RuleReminderCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.sm + 2.dp),
        )
        Column(modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 31.sp),
            )
            PreviewByline(
                boardTitle = state.boardTitle,
                authorName = state.authorName,
                modifier = Modifier.padding(top = Spacing.sm + 2.dp),
            )
            MarkdownPreviewBody(
                markdown = state.body,
                modifier = Modifier.padding(top = 14.dp, bottom = Spacing.xl),
            )
        }
    }
}

@Composable
private fun DraftRecoveryDialog(
    draft: PostDraft,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
) {
    val imageCount = remember(draft.body) { countImages(draft.body) }
    val board = draft.boardTitle ?: stringResource(R.string.composer_select_board)
    val title = draft.title.ifBlank { stringResource(R.string.composer_title_hint) }
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(NodysseyIcons.Drafts, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.composer_restore_title)) },
        text = {
            Text(
                text = if (imageCount > 0) {
                    stringResource(
                        R.string.composer_restore_body_images,
                        formatTime(draft.savedAtMillis),
                        board,
                        title,
                        imageCount,
                    )
                } else {
                    stringResource(R.string.composer_restore_body, formatTime(draft.savedAtMillis), board, title)
                },
            )
        },
        confirmButton = { Button(onClick = onContinue) { Text(stringResource(R.string.composer_restore_continue)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.composer_restore_discard)) } },
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

@Composable
private fun permissionLabel(permission: PostPermission): String =
    stringResource(
        when (permission) {
            PostPermission.PUBLIC -> R.string.composer_permission_public
            PostPermission.LEVEL_ONE -> R.string.composer_permission_level_one
            PostPermission.PRIVATE -> R.string.composer_permission_private
        },
    )

private val ComposerViewMode.labelRes: Int
    get() = when (this) {
        ComposerViewMode.CONTENT -> R.string.composer_view_content
        ComposerViewMode.PREVIEW -> R.string.composer_view_preview
        ComposerViewMode.COMPARE -> R.string.composer_view_compare
    }

private fun countImages(markdown: String): Int = IMAGE_MARKDOWN.findAll(markdown).count()

private fun formatTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

private val IMAGE_MARKDOWN = Regex("""!\[[^]]*]\([^)]+\)""")

private const val MAX_IMAGES_PER_PICK = 9
