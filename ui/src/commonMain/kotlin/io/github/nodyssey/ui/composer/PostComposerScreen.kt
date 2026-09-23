package io.github.nodyssey.ui.composer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.PostDraft
import io.github.nodyssey.data.composer.PostPermission
import io.github.nodyssey.data.composer.UploadFailure
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.webViewUrl
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_publish
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.action_save
import io.github.nodyssey.ui.resources.action_sign_in
import io.github.nodyssey.ui.resources.action_verify
import io.github.nodyssey.ui.resources.composer_board_target
import io.github.nodyssey.ui.resources.composer_body_hint
import io.github.nodyssey.ui.resources.composer_draft_saved
import io.github.nodyssey.ui.resources.composer_draft_saving
import io.github.nodyssey.ui.resources.composer_image_default_name
import io.github.nodyssey.ui.resources.composer_permission_level
import io.github.nodyssey.ui.resources.composer_permission_private
import io.github.nodyssey.ui.resources.composer_permission_public
import io.github.nodyssey.ui.resources.composer_publish_challenge
import io.github.nodyssey.ui.resources.composer_publish_failed
import io.github.nodyssey.ui.resources.composer_publish_http
import io.github.nodyssey.ui.resources.composer_publish_login_required
import io.github.nodyssey.ui.resources.composer_publish_network_failed
import io.github.nodyssey.ui.resources.composer_publish_unknown
import io.github.nodyssey.ui.resources.composer_publishing
import io.github.nodyssey.ui.resources.composer_restore_body
import io.github.nodyssey.ui.resources.composer_restore_body_images
import io.github.nodyssey.ui.resources.composer_restore_continue
import io.github.nodyssey.ui.resources.composer_restore_discard
import io.github.nodyssey.ui.resources.composer_restore_title
import io.github.nodyssey.ui.resources.composer_saving
import io.github.nodyssey.ui.resources.composer_select_board
import io.github.nodyssey.ui.resources.composer_title_count
import io.github.nodyssey.ui.resources.composer_title_hint
import io.github.nodyssey.ui.resources.composer_view_compare
import io.github.nodyssey.ui.resources.composer_view_content
import io.github.nodyssey.ui.resources.composer_view_preview
import io.github.nodyssey.ui.stardust.StardustReceiveComposeDialog
import io.github.nodyssey.ui.vote.VoteComposeDialog
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.EditorTextField
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.editor.ComposerEditorBar
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.ToolbarCustomizeSheet
import io.github.plaza.designsys.editor.rememberMarkdownEditorState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PostBody
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PostComposerRoute(
    viewModel: PostComposerViewModel,
    onClose: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onPublished: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens whatever this editor is about in the web view — the thread, or the new-post page. */
    onOpenBrowser: (String) -> Unit = onVerify,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    val pickImages =
        rememberImagePicker(
            maxItems = MAX_IMAGES_PER_PICK,
            fallbackName = stringResource(Res.string.composer_image_default_name),
            onPicked = viewModel::addImages,
        )

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
            pickImages()
        },
        onRemoveAttachment = viewModel::removeAttachment,
        onRetryAttachment = viewModel::retryUpload,
        onPublish = { if (state.isSignedIn) viewModel.publish(onPublished) else onSignIn() },
        onRetryLoad = viewModel::loadEditSource,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        onVerify = onVerify,
        onContinueDraft = viewModel::continueDraft,
        onDiscardDraft = viewModel::discardDraft,
        onToolbarChange = viewModel::setToolbar,
        onToolbarReset = viewModel::resetToolbar,
        onCreateVote = viewModel::createVote,
        onDismissVoteCreation = viewModel::dismissVoteCreation,
        payeeUid = viewModel::receiveCodePayeeUid,
        onInsertReceiveCode = viewModel::insertReceiveCode,
        modifier = modifier,
    )
}

@Composable
private fun PublishErrorSnackbar(
    state: PostComposerUiState,
    snackbarHostState: SnackbarHostState,
    onDismissed: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val message = state.publishError?.let { publishErrorMessage(it, state.publishErrorDetail) }
    val actionLabel = state.publishError?.let { error ->
        stringResource(
            when (error) {
                SiteError.LoginRequired -> Res.string.action_sign_in
                is SiteError.Cloudflare -> Res.string.action_verify
                else -> Res.string.action_retry
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
                SiteError.LoginRequired -> onSignIn()
                is SiteError.Cloudflare -> onVerify(error.url)
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
    val retry = stringResource(Res.string.action_retry)
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
    /** Re-reads the floor being edited. Only reachable from the load-failure state. */
    onRetryLoad: () -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onSignIn: () -> Unit = {},
    onVerify: (String) -> Unit,
    /**
     * Creates a vote and, on success only, runs the callback so the dialog can close.
     *
     * The lambda is the "it landed" signal: a failed creation keeps the dialog open with the site's
     * own sentence in it, because everything the author typed is still worth keeping.
     */
    onCreateVote: (String, Boolean, Boolean, List<String>, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onDismissVoteCreation: () -> Unit = {},
    payeeUid: () -> Long? = { null },
    onInsertReceiveCode: (Int, Long, String, Boolean) -> Unit = { _, _, _, _ -> },
) {
    // While a publish is in flight the request may already have created the topic; leaving now would
    // cancel the ViewModel, keep the draft, and set up a duplicate post on the next attempt. Preview
    // reuses the same handler so system back mirrors the top bar's arrow instead of closing.
    PlazaBackHandler(enabled = state.isPublishing || state.viewMode == ComposerViewMode.PREVIEW) {
        if (!state.isPublishing) onViewModeChange(ComposerViewMode.CONTENT)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // A sheet of paper rather than the grey page: the editor is one card the writer is inside,
        // so the whole screen takes the card colour and only the bar at the bottom recedes (1d).
        containerColor = LocalPlazaLayers.current.card,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ComposerTopBar(
                viewMode = state.viewMode,
                isPublishing = state.isPublishing,
                canPublish = state.canPublish,
                isEditing = state.isEditing,
                // No draft line on an edit: nothing is being autosaved, and "草稿已保存" beside a
                // post that is already published would claim the opposite of what is true.
                draftStatus = if (state.isEditing) {
                    null
                } else {
                    state.savedAtMillis?.let { stringResource(Res.string.composer_draft_saved, formatTime(it)) }
                        ?: stringResource(Res.string.composer_draft_saving)
                },
                onClose = onClose,
                onViewModeChange = onViewModeChange,
                onPublish = onPublish,
            )
        },
    ) { padding ->
        val loadError = state.editLoadError
        if (state.isLoadingEdit) {
            // The editor is not shown until the current text has arrived: an empty body on screen is
            // one 保存 away from replacing the post with nothing.
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                PlazaSpinner(Modifier.describedAsLoading())
            }
        } else if (loadError != null) {
            SiteErrorState(
                error = loadError,
                onRetry = onRetryLoad,
                onOpenBrowser = { onOpenBrowser(loadError.webViewUrl(NodeSeekSite.BASE_URL)) },
                onSignIn = onSignIn,
                onVerify = onVerify,
                modifier = Modifier.padding(padding),
            )
        } else if (state.viewMode == ComposerViewMode.PREVIEW) {
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
                onCreateVote = onCreateVote,
                onDismissVoteCreation = onDismissVoteCreation,
                payeeUid = payeeUid,
                onInsertReceiveCode = onInsertReceiveCode,
                modifier = Modifier.paddingWithKeyboard(padding),
            )
        }
    }

    state.pendingDraft?.let { draft ->
        DraftRecoveryDialog(draft = draft, onContinue = onContinueDraft, onDiscard = onDiscardDraft)
    }
}

/**
 * Close, the draft line, the two views, publish (1d).
 *
 * The title slot carries the draft status: "发布帖子" told you where you were on a screen that could
 * not be anywhere else, and "草稿 09:44" is the thing a writer actually glances up for.
 *
 * 预览 and 对照 are here rather than on the editor's bar because neither is a formatting action —
 * each changes what the whole screen shows, the way 关闭 and 发布 beside them do. They are two
 * toggles rather than the 内容/预览/对照 segmented switch this bar used to carry: the board keeps
 * one eye and nothing else, and 内容 is simply what is left when neither is lit, so tapping a lit
 * one is the way back. 关闭 stays 关闭 in every mode instead of turning into a back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerTopBar(
    viewMode: ComposerViewMode,
    isPublishing: Boolean,
    canPublish: Boolean,
    isEditing: Boolean,
    draftStatus: String?,
    onClose: () -> Unit,
    onViewModeChange: (ComposerViewMode) -> Unit,
    onPublish: () -> Unit,
) {
    TopAppBar(
        title = {
            draftStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isPublishing) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.action_cancel),
                )
            }
        },
        actions = {
            ViewModeToggle(ComposerViewMode.COMPARE, PlazaIcons.VerticalSplit, viewMode, onViewModeChange)
            ViewModeToggle(ComposerViewMode.PREVIEW, PlazaIcons.Visibility, viewMode, onViewModeChange)
            PublishButton(
                isPublishing = isPublishing,
                enabled = canPublish,
                isEditing = isEditing,
                onClick = onPublish,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalPlazaLayers.current.card),
    )
}

/** A view the screen can switch to; lit while it is showing, and tapping it lit goes back to 内容. */
@Composable
private fun ViewModeToggle(
    mode: ComposerViewMode,
    icon: ImageVector,
    current: ComposerViewMode,
    onViewModeChange: (ComposerViewMode) -> Unit,
) {
    IconToggleButton(
        checked = current == mode,
        onCheckedChange = { checked -> onViewModeChange(if (checked) mode else ComposerViewMode.CONTENT) },
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(icon, contentDescription = stringResource(mode.labelRes))
    }
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
    isEditing: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isPublishing,
        contentPadding = ButtonDefaults.SmallContentPadding,
        colors = if (isPublishing) {
            ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = Modifier.padding(end = Spacing.sm),
    ) {
        if (isPublishing) {
            PlazaSpinner(
                modifier = Modifier.describedAsLoading(),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 14.dp,
            )
            Text(
                text = stringResource(if (isEditing) Res.string.composer_saving else Res.string.composer_publishing),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        } else {
            Text(
                text = stringResource(if (isEditing) Res.string.action_save else Res.string.action_publish),
                style = MaterialTheme.typography.labelLarge,
            )
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
    onCreateVote: (String, Boolean, Boolean, List<String>, () -> Unit) -> Unit,
    onDismissVoteCreation: () -> Unit,
    payeeUid: () -> Long?,
    onInsertReceiveCode: (Int, Long, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorState = rememberMarkdownEditorState()
    val focusRequester = remember { FocusRequester() }
    var customizing by rememberSaveable { mutableStateOf(false) }
    var composingVote by rememberSaveable { mutableStateOf(false) }
    var composingReceiveCode by rememberSaveable { mutableStateOf(false) }

    // The keyboard padding is the caller's — [paddingWithKeyboard] has to sit next to the Scaffold
    // padding it consumes, and applying `imePadding` again here would put the gap right back.
    Column(modifier = modifier.fillMaxSize()) {
        ComposerOptions(state = state, onBoardSelect = onBoardSelect, onPermissionSelect = onPermissionSelect)
        // A reply has no title and no 阅读权限 of its own, so editing one shows neither — the same
        // fields the site's own editor hides for `edit-comment`.
        if (state.isThreadLevelEdit) TitleField(titleState = titleState, length = state.title.length)
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
        ComposerEditorBar(
            actions = state.toolbar.enabled,
            bodyState = bodyState,
            editorState = editorState,
            onPickImages = onPickImages,
            onCustomize = { customizing = true },
            // The bar takes focus when it is tapped, and a caret the user cannot see is a caret
            // they have lost track of.
            onFormatted = { focusRequester.requestFocus() },
            emojiPanel = { panel ->
                NodeSeekEmojiPanel(
                    onInsert = panel.onInsert,
                    onBackspace = panel.onBackspace,
                    recent = panel.recent,
                    onRecentChange = panel.onRecentChange,
                )
            },
            // The bar's own APP slot rather than an [EditorAction]: that enum is the shared pool
            // every editor draws from, and adding to it would put 插入投票 in the message, signature
            // and readme editors too — none of which can carry a vote or a 收款码.
            appMenu = {
                ComposerAppMenu(
                    onInsertVote = { composingVote = true },
                    onInsertStardust = { composingReceiveCode = true },
                )
            },
        )
    }

    if (composingVote) {
        VoteComposeDialog(
            state = state.voteCreation,
            onCreate = { title, multiple, isPublic, items ->
                onCreateVote(title, multiple, isPublic, items) { composingVote = false }
            },
            onDismiss = {
                composingVote = false
                onDismissVoteCreation()
            },
        )
    }

    if (composingReceiveCode) {
        StardustReceiveComposeDialog(
            needsSignIn = payeeUid() == null,
            onInsert = { amount, refId, description, onetime ->
                composingReceiveCode = false
                onInsertReceiveCode(amount, refId, description, onetime)
            },
            onDismiss = { composingReceiveCode = false },
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
        hint = stringResource(Res.string.composer_body_hint),
        textStyle = PostBody.copy(color = MaterialTheme.colorScheme.onSurface),
        hintStyle = PostBody,
        modifier = modifier
            .readableWidth()
            .focusRequester(focusRequester)
            .padding(horizontal = PageMargin, vertical = Spacing.md),
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
    // Borderless and large (1d): the title is the first line of the page rather than a form field,
    // and at 22sp it already reads as the heading the thread will show. It wraps instead of
    // scrolling sideways, because a 60-character title on one line is a title nobody can proofread.
    EditorTextField(
        state = titleState,
        hint = stringResource(Res.string.composer_title_hint),
        textStyle = TitleStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        // The placeholder is not bold: the weight belongs to a title that exists.
        hintStyle = TitleStyle.copy(fontWeight = FontWeight.Normal),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = TITLE_MAX_LINES),
        // Enforced at the input layer rather than trimmed afterwards: truncating in the ViewModel
        // cut composing text out from under the IME, which made the field stutter mid-word.
        // Wrapping is the only reason this is MultiLine; a title is still one line to the site, so a
        // pasted or typed line break is dropped rather than published.
        inputTransformation = NoLineBreaks.maxLength(PostComposerViewModel.MAX_TITLE_LENGTH),
        modifier = Modifier.readableWidth().padding(start = PageMargin, end = PageMargin, top = Spacing.lg),
        container = { content ->
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { content() }
                Text(
                    text = stringResource(
                        Res.string.composer_title_count,
                        length,
                        PostComposerViewModel.MAX_TITLE_LENGTH,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.xs),
                )
            }
        },
    )
}

private val TitleStyle = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
private const val TITLE_MAX_LINES = 3

private object NoLineBreaks : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        if (text.none { it == '\n' || it == '\r' }) return
        replace(0, length, text.filterNot { it == '\n' || it == '\r' })
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
    // The board is the one required field with no default, so it is called out only once there is
    // something to publish — an error outline on an untouched form is just noise.
    val boardMissing = state.boardSlug == null && state.hasContent
    // Editing a reply leaves nothing in this row: no board to move, no 阅读权限 to set, no draft
    // being saved. An empty strip of padding above the body is worse than no strip.
    if (!state.isThreadLevelEdit) return

    // Who is posting, where, and to whom (1d) — one line that reads as a sentence: 我 · 发到 技术 · 公开.
    Row(
        modifier = Modifier
            .readableWidth()
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PageMargin, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.authorName?.let { name ->
            UserAvatar(url = state.authorAvatarUrl, name = name, size = 36.dp)
        }
        // Not offered on an edit: `edit-discussion` takes no board, and moving a thread between
        // boards is a moderator action rather than something its author can do here.
        if (!state.isEditing) {
            Box {
                ComposerChip(
                    label = state.boardTitle?.let { stringResource(Res.string.composer_board_target, it) }
                        ?: stringResource(Res.string.composer_select_board),
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
        }
        Box {
            ComposerChip(
                label = permissionLabel(state.permission),
                filled = false,
                error = false,
                leading = if (state.permission == PostPermission.PUBLIC) PlazaIcons.Public else Icons.Default.Lock,
                onClick = { permissionMenuOpen = true },
            )
            DropdownMenu(expanded = permissionMenuOpen, onDismissRequest = { permissionMenuOpen = false }) {
                state.permissionOptions.forEach { permission ->
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
    }
}

/**
 * The board and 阅读权限 pills (1d).
 *
 * A chosen board is tonal primary — it is the one choice the writer has to make, and once made it
 * should read as done. Everything else is the recessed page tone. The border is there only to say
 * something: the missing-board error, or the card outline on paper where tone alone cannot.
 */
@Composable
private fun ComposerChip(
    label: String,
    filled: Boolean,
    error: Boolean,
    onClick: () -> Unit,
    leading: ImageVector? = null,
) {
    val layers = LocalPlazaLayers.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primaryContainer else layers.inset,
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = when {
            error -> BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            else -> layers.cardBorder?.let { BorderStroke(1.dp, it) }
        },
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = if (leading != null) 10.dp else Spacing.md, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            leading?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 13.sp,
                fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
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
                boardSlug = state.boardSlug,
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
    val board = draft.boardTitle ?: stringResource(Res.string.composer_select_board)
    val title = draft.title.ifBlank { stringResource(Res.string.composer_title_hint) }
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(PlazaIcons.Drafts, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.composer_restore_title)) },
        text = {
            Text(
                text = if (imageCount > 0) {
                    stringResource(
                        Res.string.composer_restore_body_images,
                        formatTime(draft.savedAtMillis),
                        board,
                        title,
                        imageCount,
                    )
                } else {
                    stringResource(Res.string.composer_restore_body, formatTime(draft.savedAtMillis), board, title)
                },
            )
        },
        confirmButton = { Button(onClick = onContinue) { Text(stringResource(Res.string.composer_restore_continue)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(Res.string.composer_restore_discard)) } },
    )
}

@Composable
private fun publishErrorMessage(error: SiteError, detail: String?): String {
    val reason = when (error) {
        SiteError.Network -> stringResource(Res.string.composer_publish_network_failed)

        SiteError.LoginRequired -> stringResource(Res.string.composer_publish_login_required)

        is SiteError.Cloudflare -> stringResource(Res.string.composer_publish_challenge)

        is SiteError.Http -> {
            val status = stringResource(Res.string.composer_publish_http, error.statusCode)
            detail?.takeIf { it.isNotBlank() && it != error.toString() }?.let { "$status：$it" } ?: status
        }

        else -> detail?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.composer_publish_unknown)
    }
    return stringResource(Res.string.composer_publish_failed, reason)
}

@Composable
private fun permissionLabel(permission: PostPermission): String =
    permission.requiredLevel?.let { level -> stringResource(Res.string.composer_permission_level, level) }
        ?: stringResource(
            if (permission == PostPermission.PUBLIC) {
                Res.string.composer_permission_public
            } else {
                Res.string.composer_permission_private
            },
        )

private val ComposerViewMode.labelRes: StringResource
    get() = when (this) {
        ComposerViewMode.CONTENT -> Res.string.composer_view_content
        ComposerViewMode.PREVIEW -> Res.string.composer_view_preview
        ComposerViewMode.COMPARE -> Res.string.composer_view_compare
    }

private fun countImages(markdown: String): Int = IMAGE_MARKDOWN.findAll(markdown).count()

// `TimeFormat.clock` rather than `java.text.DateFormat`, which is not the only reason it changed:
// the stamp is now 24-hour on every device instead of following the locale's short form. It is the
// same `09:44` a message bubble already carries, and this is the app's only other one.
private fun formatTime(timestamp: Long): String = TimeFormat.clock(timestamp)

private val IMAGE_MARKDOWN = Regex("""!\[[^]]*]\([^)]+\)""")

private const val MAX_IMAGES_PER_PICK = 9

/** The page's text margin in 1d — wider than the 16dp list margin, because this is a page, not a list. */
private val PageMargin = 20.dp
