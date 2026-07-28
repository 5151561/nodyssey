package io.github.nsreader.ui.composer

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nsreader.R
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.composer.ImageAttachment
import io.github.nsreader.data.composer.PickedImage
import io.github.nsreader.data.composer.UploadFailure
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.theme.CommentBody
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth
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
    onBodyChange: (String) -> Unit,
    onClearQuote: () -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    onPickImages: (List<PickedImage>) -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onRetryFailedUploads: () -> Unit,
    onPublish: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Above the early return on purpose: the host stays composed while the sheet is closed, so
    // this is the one place the recents survive both the emoji panel and the sheet being dismissed.
    var recentEmoji by rememberSaveable { mutableStateOf(listOf<String>()) }
    if (!state.visible) return
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_PICK),
    ) { uris -> onPickImages(uris.toPickedImages(context)) }
    val launchPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.previewing,
            // Shared axis Y: the preview rises out of the sheet and sinks back into it, so the two
            // read as one surface changing state rather than two screens swapping.
            enter = fadeIn(spring(stiffness = SPRING_STIFFNESS)) +
                slideInVertically(spring(stiffness = SPRING_STIFFNESS)) { it / SLIDE_FRACTION },
            exit = fadeOut() + slideOutVertically { it / SLIDE_FRACTION },
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
            onBodyChange = onBodyChange,
            onClearQuote = onClearQuote,
            onPreview = { onPreviewChange(true) },
            onPickImages = launchPicker,
            onRemoveAttachment = onRemoveAttachment,
            onRetryAttachment = onRetryAttachment,
            onRetryFailedUploads = onRetryFailedUploads,
            onPublish = onPublish,
            onClearError = onClearError,
            recentEmoji = recentEmoji,
            onRecentEmojiChange = { recentEmoji = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyEditorSheet(
    state: ReplyComposerUiState,
    onDismiss: () -> Unit,
    onBodyChange: (String) -> Unit,
    onClearQuote: () -> Unit,
    onPreview: () -> Unit,
    onPickImages: () -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onRetryFailedUploads: () -> Unit,
    onPublish: () -> Unit,
    onClearError: () -> Unit,
    recentEmoji: List<String>,
    onRecentEmojiChange: (List<String>) -> Unit,
) {
    var bodyValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.body, TextRange(state.body.length)))
    }
    var emojiOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.body) {
        if (state.body != bodyValue.text) {
            bodyValue = TextFieldValue(state.body, TextRange(state.body.length))
        }
    }
    fun edit(next: TextFieldValue) {
        bodyValue = next
        onBodyChange(next.text)
    }

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
                    text = state.quote?.let {
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
                IconButton(onClick = onDismiss, enabled = !state.isPublishing) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.quote?.let { quote ->
                QuoteChip(
                    excerpt = quote.excerpt,
                    onClear = onClearQuote,
                    modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xs),
                )
            }
            BasicTextField(
                value = bodyValue,
                onValueChange = ::edit,
                textStyle = CommentBody.copy(
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .readableWidth()
                    .heightIn(min = MIN_EDITOR_HEIGHT, max = MAX_EDITOR_HEIGHT)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                decorationBox = { inner ->
                    // Fills the width it was given: `readableWidth` centres what it wraps, so a
                    // decoration that measures to its content would centre a half-typed reply.
                    Box(Modifier.fillMaxWidth()) {
                        if (bodyValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.post_reply_editor_hint),
                                style = CommentBody.copy(fontSize = 16.sp, lineHeight = 26.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
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
            EditorToolbar(
                actions = REPLY_ACTIONS,
                active = if (emojiOpen) setOf(EditorAction.EMOJI) else emptySet(),
                showDivider = false,
                onAction = { action ->
                    when (action) {
                        EditorAction.IMAGE -> onPickImages()

                        EditorAction.PREVIEW -> {
                            keyboard?.hide()
                            onPreview()
                        }

                        EditorAction.EMOJI -> {
                            emojiOpen = !emojiOpen
                            if (emojiOpen) keyboard?.hide()
                        }

                        else -> {
                            emojiOpen = false
                            edit(applyMarkdown(bodyValue, action))
                        }
                    }
                },
                trailing = {
                    PublishReplyButton(
                        isPublishing = state.isPublishing,
                        enabled = state.canPublish,
                        onClick = onPublish,
                    )
                },
            )
            if (emojiOpen) {
                EmojiPanel(
                    onInsert = { text -> edit(insertText(bodyValue, text)) },
                    onBackspace = { edit(bodyValue.deleteBackwards()) },
                    recent = recentEmoji,
                    onRecentChange = onRecentEmojiChange,
                )
            }
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
                        text = state.quote?.let {
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
                state.quote?.let { quote ->
                    QuoteReference(floor = quote.floor, author = quote.author, excerpt = quote.excerpt)
                }
                MarkdownPreviewBody(markdown = state.body)
            }
        }
    }
}

/** The dismissible quote context above the reply field (6d). */
@Composable
private fun QuoteChip(
    excerpt: String,
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
            Icon(NodeSeekIcons.FormatQuote, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                text = excerpt,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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

/** How the quote reads once published: an addressed chip, then the quoted floor. */
@Composable
private fun QuoteReference(
    floor: Int,
    author: String,
    excerpt: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        Row {
            Box(
                Modifier
                    .width(3.dp)
                    .heightIn(min = 20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                text = excerpt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md),
            )
        }
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
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
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

private val REPLY_ACTIONS = listOf(
    EditorAction.BOLD,
    EditorAction.CODE,
    EditorAction.QUOTE,
    EditorAction.MENTION,
    EditorAction.IMAGE,
    EditorAction.EMOJI,
    EditorAction.PREVIEW,
)

private val MIN_EDITOR_HEIGHT = 96.dp
private val MAX_EDITOR_HEIGHT = 260.dp
private const val MAX_IMAGES_PER_PICK = 9
private const val SLIDE_FRACTION = 6
private const val SPRING_STIFFNESS = 400f
