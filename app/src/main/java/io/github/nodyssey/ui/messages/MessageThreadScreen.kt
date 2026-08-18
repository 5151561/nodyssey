package io.github.nodyssey.ui.messages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.composer.AttachmentTray
import io.github.nodyssey.ui.composer.NodeSeekEmojiPanel
import io.github.nodyssey.ui.composer.toPickedImages
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.richtext.parseMarkdown
import io.github.plaza.designsys.component.EditorTextField
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.ThreadRow
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.MarkdownEditorBar
import io.github.plaza.designsys.editor.MarkdownEditorState
import io.github.plaza.designsys.editor.ToolbarCustomizeSheet
import io.github.plaza.designsys.editor.rememberMarkdownEditorState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard

@Composable
fun MessageThreadRoute(
    viewModel: MessageThreadViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    /** Bubble-content links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MessageThreadScreen(
        state = state,
        draftState = viewModel.draftState,
        onBack = onBack,
        showBackButton = showBackButton,
        onSignIn = onSignIn,
        onVerify = onVerify,
        onOpenBrowser = onOpenBrowser,
        onLinkClick = onLinkClick,
        onRetryLoad = viewModel::refresh,
        onToggleMarkdown = viewModel::toggleMarkdown,
        onSend = viewModel::send,
        onRetrySend = viewModel::retry,
        onPickImages = viewModel::addImages,
        onRemoveAttachment = viewModel::removeAttachment,
        onRetryAttachment = viewModel::retryUpload,
        onToolbarChange = viewModel::setToolbar,
        onToolbarReset = viewModel::resetToolbar,
        modifier = modifier,
    )
}

/** Board 7f — full screen, so the tab bar stays out of a conversation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageThreadScreen(
    state: MessageThreadUiState,
    draftState: TextFieldState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onToggleMarkdown: () -> Unit,
    onSend: () -> Unit,
    onRetrySend: (String) -> Unit,
    onPickImages: (List<PickedImage>) -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onToolbarChange: (List<EditorAction>) -> Unit,
    onToolbarReset: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the conversation draws its own way back.
     *
     * False when it is the detail half of a two-pane layout: the list it came from is still on
     * screen beside it, so there is nothing for an arrow to return to.
     */
    showBackButton: Boolean = true,
    /** Bubble-content links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
) {
    val webUrl = NodeSeekSite.BASE_URL + NodeSeekSite.messageThreadWebPath(state.uid)
    val editorState = rememberMarkdownEditorState()
    var customizing by rememberSaveable { mutableStateOf(false) }
    // Turning MD off takes the strip away, and neither the panel nor the sheet it opened may be left
    // behind on a bar that no longer has a key to close them.
    LaunchedEffect(state.isMarkdown) {
        if (!state.isMarkdown) {
            editorState.closeEmoji()
            customizing = false
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        UserAvatar(url = state.avatarUrl, name = state.userName, size = 32.dp)
                        Column {
                            Text(
                                stringResource(R.string.message_thread_title, state.userName),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                state.level?.let {
                                    stringResource(R.string.message_thread_subtitle_level, state.uid, it)
                                } ?: stringResource(R.string.message_thread_subtitle, state.uid),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = { ThreadMenu(onOpenBrowser = { onOpenBrowser(webUrl) }) },
            )
        },
    ) { padding ->
        Column(Modifier.paddingWithKeyboard(padding).fillMaxSize()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // fillMaxWidth, or the box is only as wide as whatever is inside it — which for the
            // empty state is one line of text, and centring inside that put it against the left edge.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.messages.isEmpty() -> LoadingState()

                    state.error != null && state.messages.isEmpty() ->
                        SiteErrorState(
                            error = state.error,
                            onRetry = onRetryLoad,
                            onOpenBrowser = {
                                if (state.error == SiteError.LoginRequired) onSignIn() else onVerify()
                            },
                        )

                    state.messages.isEmpty() ->
                        Text(
                            stringResource(R.string.message_thread_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = Spacing.xl),
                        )

                    else -> MessageBubbles(state, onLinkClick, onRetrySend)
                }
            }
            MessageComposer(
                draftState = draftState,
                state = state,
                editorState = editorState,
                onToggleMarkdown = onToggleMarkdown,
                onSend = onSend,
                onPickImages = onPickImages,
                onRemoveAttachment = onRemoveAttachment,
                onRetryAttachment = onRetryAttachment,
                onCustomize = { customizing = true },
            )
        }
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
private fun MessageBubbles(
    state: MessageThreadUiState,
    onOpenBrowser: (String) -> Unit,
    onRetrySend: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    /*
     * The rows are built up front rather than emitted inline because the day separators are items
     * too. Scrolling to `messages.lastIndex` stopped short by one position per separator, which on a
     * conversation spanning a few days left the newest message off screen — the one place the screen
     * must always land.
     */
    val rows = remember(state.messages, state.nowMillis) { threadRows(state.messages, state.nowMillis) }
    /*
     * Anchored at the bottom rather than scrolled there.
     *
     * `reverseLayout` measures from the last row up, so the newest message stays against the message
     * bar whatever happens to the viewport — which is the answer to the keyboard as much as to a new
     * message. Scrolling to the end in an effect could not be: the keyboard shrinks the list over
     * several frames, and a scroll that finished on the first of them left the thread short.
     *
     * `asReversed()` is a view, not a copy, and it puts row 0 at the bottom — so the day separators
     * still read in the order they were built.
     */
    val newestFirst = remember(rows) { rows.asReversed() }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(newestFirst, key = ThreadRow::key) { row ->
            when (row) {
                is ThreadRow.Day -> DayDivider(row.label)

                is ThreadRow.Bubble ->
                    MessageBubbleRow(
                        message = row.message,
                        onOpenBrowser = onOpenBrowser,
                        onRetrySend = { onRetrySend(row.message.id) },
                    )
            }
        }
    }
}

internal sealed interface ThreadRow {
    val key: String

    data class Day(val label: String) : ThreadRow {
        override val key get() = "day-$label"
    }

    data class Bubble(val message: MessageBubble) : ThreadRow {
        override val key get() = message.id
    }
}

/** A separator opens the thread and reappears whenever the conversation crosses into a new day. */
internal fun threadRows(
    messages: List<MessageBubble>,
    nowMillis: Long,
): List<ThreadRow> {
    val rows = mutableListOf<ThreadRow>()
    var currentDay: String? = null
    messages.forEach { message ->
        val label = message.sentAtMillis?.let { TimeFormat.messageDivider(it, nowMillis) }
        // The label carries a clock as well as a day, so only the day half decides.
        val day = label?.substringBefore(' ')
        if (label != null && day != currentDay) {
            rows += ThreadRow.Day(label)
            currentDay = day
        }
        rows += ThreadRow.Bubble(message)
    }
    return rows
}

@Composable
private fun DayDivider(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MessageBubbleRow(
    message: MessageBubble,
    onOpenBrowser: (String) -> Unit,
    onRetrySend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxWidth(BUBBLE_MAX_WIDTH)
                .wrapContentWidthTo(message.isMine)
                .clip(
                    if (message.isMine) {
                        RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
                    } else {
                        RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
                    },
                ).background(
                    if (message.isMine) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color =
                    if (message.isMine) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            if (message.isMarkdown) {
                PostRichContent(
                    nodes = parseMarkdown(message.content),
                    onLinkClick = onOpenBrowser,
                    onImageClick = onOpenBrowser,
                    textStyle = textStyle,
                )
            } else {
                Text(message.content, style = textStyle)
            }
        }
        MessageStatusLine(message = message, onRetrySend = onRetrySend)
    }
}

@Composable
private fun MessageStatusLine(
    message: MessageBubble,
    onRetrySend: () -> Unit,
) {
    val clock = message.sentAtMillis?.let(TimeFormat::clock) ?: message.sentAtText
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        when (message.status) {
            SendStatus.SENDING -> {
                CircularProgressIndicator(
                    strokeWidth = 1.6.dp,
                    modifier = Modifier.size(10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.message_status_sending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SendStatus.FAILED -> {
                Icon(
                    PlazaIcons.ErrorCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    // The server's reason when it gave one — retrying a block never succeeds.
                    text = message.failureReason ?: stringResource(R.string.message_status_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onRetrySend)
                        .padding(horizontal = 2.dp),
                )
            }

            SendStatus.SENT ->
                when {
                    message.isMine && clock != null -> {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            stringResource(R.string.message_status_sent, clock),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    clock != null ->
                        Text(
                            clock,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
        }
    }
}

/**
 * The message bar: one pill for the text, a toggle and a send key sitting on its bottom line.
 *
 * Built out of a `BasicTextField` and a `Surface` rather than a filled `TextField`, because a filled
 * field reserves the room a floating label would need and is 56dp tall before it holds anything —
 * next to a 44dp send key that read as three mismatched blocks. The field grows with the draft up to
 * [MAX_INPUT_LINES] and the two keys stay on the last line, which is what makes the row settle.
 */
@Composable
private fun MessageInputBar(
    draftState: TextFieldState,
    isMarkdown: Boolean,
    canSend: Boolean,
    onToggleMarkdown: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MarkdownToggle(isMarkdown = isMarkdown, onToggle = onToggleMarkdown)
            MessageDraftField(
                draftState = draftState,
                isMarkdown = isMarkdown,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                colors =
                IconButtonDefaults.filledIconButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.size(INPUT_CONTROL_SIZE),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.message_send),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageDraftField(
    draftState: TextFieldState,
    isMarkdown: Boolean,
    modifier: Modifier = Modifier,
) {
    EditorTextField(
        state = draftState,
        hint =
        stringResource(
            if (isMarkdown) R.string.message_input_hint_markdown else R.string.message_input_hint_plain,
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        // One line of placeholder, elided: the pill is 44dp tall when empty and a wrapping hint
        // would grow it before anything has been typed.
        hintMaxLines = 1,
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_INPUT_LINES),
        modifier = modifier,
        container = { content ->
            Surface(
                shape = RoundedCornerShape(INPUT_CONTROL_SIZE / 2),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(
                    modifier =
                    Modifier
                        .heightIn(min = INPUT_CONTROL_SIZE)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    content()
                }
            }
        },
    )
}

/**
 * The formatting strip, shown only while MD is on (7f + C6).
 *
 * Off, the server takes the text verbatim, and a toolbar that inserted `**` would be offering syntax
 * that arrives as literal asterisks — so the strip is not disabled, it is absent, and the toggle is
 * the one control that decides.
 *
 * The strip sits above the message bar rather than below it, and the emoji panel below: the panel
 * stands in for the keyboard and belongs where the keyboard was, while the keys belong next to the
 * text they format. [MarkdownEditorBar] emits them in that order around its content slot.
 */
@Composable
private fun MessageComposer(
    draftState: TextFieldState,
    state: MessageThreadUiState,
    editorState: MarkdownEditorState,
    onToggleMarkdown: () -> Unit,
    onSend: () -> Unit,
    onPickImages: (List<PickedImage>) -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onCustomize: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_PICK),
    ) { uris -> onPickImages(uris.toPickedImages(context)) }

    val inputBar: @Composable () -> Unit = {
        MessageInputBar(
            draftState = draftState,
            isMarkdown = state.isMarkdown,
            canSend = state.canSend,
            onToggleMarkdown = onToggleMarkdown,
            onSend = onSend,
        )
    }

    Column {
        // Outside the MD branch: an upload started before the toggle was flipped is still running,
        // and its cell is the only place the user can see that, or cancel it.
        AttachmentTray(
            attachments = state.attachments,
            onRemove = onRemoveAttachment,
            onRetry = onRetryAttachment,
        )
        if (state.isMarkdown) {
            MarkdownEditorBar(
                actions = state.toolbar.enabled,
                bodyState = draftState,
                editorState = editorState,
                onPickImages = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onCustomize = onCustomize,
                emojiPanel = { panel ->
                    NodeSeekEmojiPanel(
                        onInsert = panel.onInsert,
                        onBackspace = panel.onBackspace,
                        recent = panel.recent,
                        onRecentChange = panel.onRecentChange,
                    )
                },
                content = { inputBar() },
            )
        } else {
            inputBar()
        }
    }
}

/**
 * The site's MD On/Off switch, as a Material toggle button rather than a hand-drawn switch.
 *
 * The previous one was a 48×44 slab holding a 30×16 track and the letters MD — two controls' worth
 * of furniture for one bit. `ToggleButton` says the same thing in the same square as the send key,
 * and brings the checked-state shape change and colours with it. "MD" alone tells a screen reader
 * nothing, so the button still carries the full name.
 */
@Composable
private fun MarkdownToggle(
    isMarkdown: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(R.string.message_markdown_toggle)
    ToggleButton(
        checked = isMarkdown,
        onCheckedChange = { onToggle() },
        shapes = ToggleButtonDefaults.shapes(),
        contentPadding = PaddingValues(0.dp),
        modifier =
        Modifier
            .size(INPUT_CONTROL_SIZE)
            .semantics { contentDescription = label },
    ) {
        Text(
            text = stringResource(R.string.message_markdown_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ThreadMenu(onOpenBrowser: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_open_in_browser)) },
            onClick = {
                expanded = false
                onOpenBrowser()
            },
        )
    }
}

/**
 * A bubble hugs its text; only the 78 % cap comes from the parent.
 *
 * `fillMaxWidth` claims the fraction and `wrapContentWidth` then releases the minimum-width
 * constraint it would otherwise impose — the same two-step the readable-width cap uses.
 */
private fun Modifier.wrapContentWidthTo(isMine: Boolean): Modifier =
    this.wrapContentWidth(align = if (isMine) Alignment.End else Alignment.Start)

private const val BUBBLE_MAX_WIDTH = 0.78f
private const val MAX_INPUT_LINES = 5
private const val MAX_IMAGES_PER_PICK = 9

/** One height for the MD toggle, the send key and the empty draft pill, so the bar reads as a row. */
private val INPUT_CONTROL_SIZE = 44.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun MessageThreadPreview() {
    val now = 1_785_000_000_000L
    PlazaTheme {
        MessageThreadScreen(
            state =
            MessageThreadUiState(
                uid = 4471,
                userName = "iwil",
                level = 4,
                nowMillis = now,
                messages =
                listOf(
                    MessageBubble(
                        id = "1",
                        isMine = false,
                        content = "改名的事我问过管理，说要等 UID 显示上线",
                        sentAtMillis = now - 40 * 60_000L,
                        sentAtText = null,
                        isMarkdown = true,
                        status = SendStatus.SENT,
                    ),
                    MessageBubble(
                        id = "2",
                        isMine = true,
                        content = "那大概什么时候？我这 ID 打错字快两年了",
                        sentAtMillis = now - 37 * 60_000L,
                        sentAtText = null,
                        isMarkdown = true,
                        status = SendStatus.SENT,
                    ),
                    MessageBubble(
                        id = "3",
                        isMine = false,
                        content = "没给时间点。你可以先在 [求教如何改用户名](/post-1-1) 里顶一下",
                        sentAtMillis = now - 29 * 60_000L,
                        sentAtText = null,
                        isMarkdown = true,
                        status = SendStatus.SENT,
                    ),
                    MessageBubble(
                        id = "4",
                        isMine = true,
                        content = "行，我发个投票试试",
                        sentAtMillis = now - 60_000L,
                        sentAtText = null,
                        isMarkdown = true,
                        status = SendStatus.SENDING,
                    ),
                    MessageBubble(
                        id = "5",
                        isMine = true,
                        content = "顺便问下星辰能转账吗",
                        sentAtMillis = now,
                        sentAtText = null,
                        isMarkdown = true,
                        status = SendStatus.FAILED,
                    ),
                ),
            ),
            draftState = remember { TextFieldState() },
            onBack = {},
            onSignIn = {},
            onVerify = {},
            onOpenBrowser = {},
            onRetryLoad = {},
            onToggleMarkdown = {},
            onSend = {},
            onRetrySend = {},
            onPickImages = {},
            onRemoveAttachment = {},
            onRetryAttachment = {},
            onToolbarChange = {},
            onToolbarReset = {},
        )
    }
}
