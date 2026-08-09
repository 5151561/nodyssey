package io.github.nodyssey.ui.composer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.R
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.composer.UploadFailure
import io.github.plaza.designsys.component.EditorTextField
import io.github.plaza.designsys.component.NodysseyIcons
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.EditorToolbarDefaults
import io.github.plaza.designsys.editor.MarkdownEditorBar
import io.github.plaza.designsys.editor.MarkdownEditorState
import io.github.plaza.designsys.editor.ToolbarCustomizeSheet
import io.github.plaza.designsys.editor.rememberMarkdownEditorState
import io.github.plaza.designsys.theme.CommentBody
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import java.text.DateFormat
import java.util.Date

/**
 * The reply editor: a modal sheet (6d) that expands to a full-screen preview (C4).
 *
 * Hosted as a sibling of the thread rather than inside it, because both halves cover the screen —
 * the sheet in its own window, the preview over everything — and neither belongs in the thread's
 * own layout. The two are mutually exclusive: the preview replaces the sheet rather than stacking
 * on it, which is what the shared-axis transition in the board describes.
 */
@Composable
fun ReplyComposerHost(
    state: ReplyComposerUiState,
    onDismiss: () -> Unit,
    bodyState: TextFieldState,
    onClearReplyTo: () -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    onPickImages: (List<PickedImage>) -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onRetryFailedUploads: () -> Unit,
    onPublish: () -> Unit,
    onClearError: () -> Unit,
    onToolbarChange: (List<EditorAction>) -> Unit,
    onToolbarReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Above the early return on purpose: the host stays composed while the sheet is closed, so
    // this is the one place the recents survive both the emoji panel and the sheet being dismissed.
    val editorState = rememberMarkdownEditorState()
    // The panel is part of the sheet even though its state is not, so it goes down with it — the
    // recents are what outlive the dismissal, not a half-open drawer.
    LaunchedEffect(state.visible) { if (!state.visible) editorState.closeEmoji() }
    if (!state.visible) return
    // Hosted here rather than inside the editor sheet: it is a sheet too, and a sheet opened from
    // inside another sheet's content stacks two dialog windows for no reason. As siblings the wrench
    // panel simply covers the editor, which is what it should look like anyway.
    var customizing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_PICK),
    ) { uris -> onPickImages(uris.toPickedImages(context)) }
    val launchPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val motionScheme = MaterialTheme.motionScheme

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.previewing,
            // Shared axis Y: the preview rises out of the sheet and sinks back into it, so the two
            // read as one surface changing state rather than two screens swapping.
            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                slideInVertically(motionScheme.defaultSpatialSpec()) { it / SLIDE_FRACTION },
            exit = fadeOut(motionScheme.fastEffectsSpec()) +
                slideOutVertically(motionScheme.fastSpatialSpec()) { it / SLIDE_FRACTION },
        ) {
            ReplyPreviewScreen(
                state = state,
                onBack = { onPreviewChange(false) },
                onPublish = onPublish,
                onClearError = onClearError,
            )
        }
    }

    if (!state.previewing) {
        ReplyEditorSheet(
            state = state,
            onDismiss = onDismiss,
            bodyState = bodyState,
            onClearReplyTo = onClearReplyTo,
            onPreview = { onPreviewChange(true) },
            onPickImages = launchPicker,
            onRemoveAttachment = onRemoveAttachment,
            onRetryAttachment = onRetryAttachment,
            onRetryFailedUploads = onRetryFailedUploads,
            onPublish = onPublish,
            onClearError = onClearError,
            editorState = editorState,
            onCustomize = { customizing = true },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyEditorSheet(
    state: ReplyComposerUiState,
    onDismiss: () -> Unit,
    bodyState: TextFieldState,
    onClearReplyTo: () -> Unit,
    onPreview: () -> Unit,
    onPickImages: () -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onRetryFailedUploads: () -> Unit,
    onPublish: () -> Unit,
    onClearError: () -> Unit,
    editorState: MarkdownEditorState,
    onCustomize: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        // Guarded like the close button and the BackHandler: while a publish is in flight, a swipe
        // or scrim tap must not be the one dismiss path that still works.
        onDismissRequest = { if (!state.isPublishing) onDismiss() },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            Row(
                modifier = Modifier.padding(start = Spacing.xl, end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.replyTo?.let {
                        stringResource(R.string.post_reply_editor_title_floor, it.floor, it.author)
                    } ?: stringResource(R.string.post_reply_editor_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                state.savedAtMillis?.let {
                    Text(
                        text = stringResource(R.string.post_reply_draft_saved, formatTime(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The sheet's own chrome is where a view switch belongs — this row is what the post
                // editor's top bar is, and it is the only bar the sheet has.
                IconButton(
                    onClick = {
                        // The preview covers the sheet, so the IME has to be gone before it rises —
                        // otherwise it comes back to a keyboard over a screen that has no field.
                        keyboard?.hide()
                        onPreview()
                    },
                    enabled = !state.isPublishing,
                ) {
                    Icon(
                        imageVector = NodysseyIcons.Visibility,
                        contentDescription = stringResource(R.string.action_preview),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, enabled = !state.isPublishing) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.replyTo?.let { replyTo ->
                ReplyTargetChip(
                    replyTo = replyTo,
                    onClear = onClearReplyTo,
                    modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xs),
                )
            }
            // The default container fills the width it was given, which is what this field needs:
            // `readableWidth` centres what it wraps, so a decoration that measured to its content
            // would centre a half-typed reply.
            EditorTextField(
                state = bodyState,
                hint = stringResource(R.string.post_reply_editor_hint),
                textStyle = CommentBody.copy(
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                hintStyle = CommentBody.copy(fontSize = 16.sp, lineHeight = 26.sp),
                modifier = Modifier
                    .readableWidth()
                    .heightIn(min = MIN_EDITOR_HEIGHT, max = MAX_EDITOR_HEIGHT)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )
            AttachmentTray(
                attachments = state.attachments,
                onRemove = onRemoveAttachment,
                onRetry = onRetryAttachment,
            )
            ComposerErrorStrip(
                error = state.publishError,
                detail = state.publishErrorDetail,
                failedUploads = state.failedUploadCount,
                uploadFailure = state.uploadFailure,
                uploadErrorDetail = state.uploadErrorDetail,
                onRetryPublish = onPublish,
                onRetryUploads = onRetryFailedUploads,
                onDismiss = onClearError,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
            MarkdownEditorBar(
                actions = state.toolbar.enabled,
                bodyState = bodyState,
                editorState = editorState,
                showDivider = false,
                // The one strip in the app under 48dp: it keeps 发布 pinned at its end, and six full
                // keys plus that button want 392dp on a 360dp screen.
                keySize = EditorToolbarDefaults.CompactKeySize,
                onPickImages = onPickImages,
                onCustomize = onCustomize,
                emojiPanel = { panel ->
                    EmojiPanel(
                        onInsert = panel.onInsert,
                        onBackspace = panel.onBackspace,
                        recent = panel.recent,
                        onRecentChange = panel.onRecentChange,
                    )
                },
                trailing = {
                    PublishReplyButton(
                        isPublishing = state.isPublishing,
                        enabled = state.canPublish,
                        onClick = onPublish,
                    )
                },
            )
        }
    }
}

/** C4: the sheet's content, full screen, rendered exactly the way the thread will render it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyPreviewScreen(
    state: ReplyComposerUiState,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    onClearError: () -> Unit,
) {
    BackHandler(enabled = !state.isPublishing, onBack = onBack)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = {
                    Text(
                        text = state.replyTo?.let {
                            stringResource(R.string.post_reply_preview_title_floor, it.floor)
                        } ?: stringResource(R.string.post_reply_preview_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 16.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isPublishing) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    PublishReplyButton(
                        isPublishing = state.isPublishing,
                        enabled = state.canPublish,
                        onClick = onPublish,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ComposerErrorStrip(
                error = state.publishError,
                detail = state.publishErrorDetail,
                failedUploads = 0,
                uploadFailure = null,
                uploadErrorDetail = null,
                onRetryPublish = onPublish,
                onRetryUploads = {},
                onDismiss = onClearError,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp),
            ) {
                // Only the 回复 reference: any 引用 is part of the body below, and the Markdown
                // preview already renders it as the blockquote it will become.
                state.replyTo?.let { replyTo ->
                    ReplyReference(floor = replyTo.floor, author = replyTo.author)
                }
                MarkdownPreviewBody(markdown = state.body)
            }
        }
    }
}

/**
 * The dismissible 回复 target above the reply field (6d).
 *
 * Only 回复 gets a chip, because only 回复 is a property of the comment as a whole and can only be
 * one floor. A 引用 is text in the body, visible and editable there, and giving it a chip too would
 * imply it could be dismissed the same way.
 */
@Composable
private fun ReplyTargetChip(
    replyTo: FloorReference,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        ) {
            Icon(NodysseyIcons.Reply, contentDescription = null, modifier = Modifier.size(15.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.post_quote_reply, replyTo.author, "#${replyTo.floor}"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (replyTo.excerpt.isNotBlank()) {
                    Text(
                        text = replyTo.excerpt,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.post_reply_quote_remove),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** How the 回复 reads once published: the addressed floor, ahead of the body it belongs to. */
@Composable
private fun ReplyReference(
    floor: Int,
    author: String,
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = "@$author ${stringResource(R.string.post_quote_prefix, floor)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/**
 * Failures, inline.
 *
 * The post editor puts these on a Snackbar; the reply editor cannot, because its own sheet is a
 * separate window that a Snackbar would appear behind. Inline also keeps the message on screen
 * next to the text it is talking about, which for "草稿已保留" is the reassurance that matters.
 */
@Composable
private fun ComposerErrorStrip(
    error: NodeSeekError?,
    detail: String?,
    failedUploads: Int,
    uploadFailure: UploadFailure?,
    uploadErrorDetail: String?,
    onRetryPublish: () -> Unit,
    onRetryUploads: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when {
        error != null -> stringResource(R.string.post_reply_publish_failed, replyErrorReason(error, detail))
        failedUploads > 0 -> uploadFailureText(failedUploads, uploadFailure, uploadErrorDetail)
        else -> return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (error != null) {
                        onDismiss()
                        onRetryPublish()
                    } else {
                        onRetryUploads()
                    }
                },
                contentPadding = PaddingValues(horizontal = Spacing.md),
            ) {
                Text(stringResource(R.string.action_retry), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun PublishReplyButton(
    isPublishing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isPublishing,
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = modifier.height(40.dp),
    ) {
        if (isPublishing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = stringResource(if (isPublishing) R.string.composer_publishing else R.string.action_publish),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = Spacing.xs + 2.dp),
        )
    }
}

@Composable
private fun replyErrorReason(error: NodeSeekError, detail: String?): String = when (error) {
    NodeSeekError.Network -> stringResource(R.string.composer_publish_network_failed)

    NodeSeekError.LoginRequired -> stringResource(R.string.composer_publish_login_required)

    NodeSeekError.Cloudflare -> stringResource(R.string.composer_publish_challenge)

    // The site's own sentence beats a status code whenever it sent one: a rejected reply comes back
    // as a 400 carrying "内容不能为空" or the duplicate-post refusal, and "服务器返回 HTTP 400"
    // would tell the user nothing they can act on.
    is NodeSeekError.Http ->
        detail?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.composer_publish_http, error.statusCode)

    else -> detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.post_reply_publish_unavailable)
}

private fun formatTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

private val MIN_EDITOR_HEIGHT = 96.dp
private val MAX_EDITOR_HEIGHT = 260.dp
private const val MAX_IMAGES_PER_PICK = 9
private const val SLIDE_FRACTION = 6
