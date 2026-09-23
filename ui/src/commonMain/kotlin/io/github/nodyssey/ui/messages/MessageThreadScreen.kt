package io.github.nodyssey.ui.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.NodeSeekStickers
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.contentPreview
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.webViewUrl
import io.github.nodyssey.ui.composer.AttachmentTray
import io.github.nodyssey.ui.composer.NodeSeekEmojiPanel
import io.github.nodyssey.ui.composer.rememberImagePicker
import io.github.nodyssey.ui.notifications.previewText
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_copy
import io.github.nodyssey.ui.resources.action_more
import io.github.nodyssey.ui.resources.action_open_in_browser
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.composer_image_default_name
import io.github.nodyssey.ui.resources.message_bubble_actions
import io.github.nodyssey.ui.resources.message_copied
import io.github.nodyssey.ui.resources.message_input_hint_markdown
import io.github.nodyssey.ui.resources.message_input_hint_plain
import io.github.nodyssey.ui.resources.message_markdown_toggle
import io.github.nodyssey.ui.resources.message_quote_action
import io.github.nodyssey.ui.resources.message_quote_chip
import io.github.nodyssey.ui.resources.message_quote_chip_mine
import io.github.nodyssey.ui.resources.message_quote_remove
import io.github.nodyssey.ui.resources.message_send
import io.github.nodyssey.ui.resources.message_status_failed
import io.github.nodyssey.ui.resources.message_status_sending
import io.github.nodyssey.ui.resources.message_status_sent
import io.github.nodyssey.ui.resources.message_thread_empty
import io.github.nodyssey.ui.resources.message_thread_open_space
import io.github.nodyssey.ui.resources.message_thread_subtitle
import io.github.nodyssey.ui.resources.message_thread_subtitle_level
import io.github.nodyssey.ui.resources.message_thread_title
import io.github.nodyssey.ui.resources.message_thread_title_unknown
import io.github.nodyssey.ui.resources.message_tool_customize
import io.github.nodyssey.ui.resources.message_tool_markdown_off
import io.github.nodyssey.ui.resources.message_tool_markdown_on
import io.github.nodyssey.ui.resources.message_tools
import io.github.nodyssey.ui.resources.notification_time_pair
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.richtext.parseMarkdown
import io.github.plaza.designsys.component.EditorTextField
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.QuotePreview
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.ToolbarCustomizeSheet
import io.github.plaza.designsys.editor.applyMarkdown
import io.github.plaza.designsys.editor.deleteBackwards
import io.github.plaza.designsys.editor.icon
import io.github.plaza.designsys.editor.insertText
import io.github.plaza.designsys.editor.label
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.cardShadow
import io.github.plaza.designsys.theme.floatShadow
import org.jetbrains.compose.resources.stringResource

@Composable
fun MessageThreadRoute(
    viewModel: MessageThreadViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onOpenSpace: () -> Unit,
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
        onOpenSpace = onOpenSpace,
        onLinkClick = onLinkClick,
        onRetryLoad = viewModel::refresh,
        onToggleMarkdown = viewModel::toggleMarkdown,
        onSend = viewModel::send,
        onRetrySend = viewModel::retry,
        onQuote = viewModel::quote,
        onRemoveQuote = viewModel::removeQuote,
        onPickImages = viewModel::addImages,
        onRemoveAttachment = viewModel::removeAttachment,
        onRetryAttachment = viewModel::retryUpload,
        onToolbarChange = viewModel::setToolbar,
        onToolbarReset = viewModel::resetToolbar,
        modifier = modifier,
    )
}

/**
 * Board 7f, redrawn as 3d and 3e — full screen, so the tab bar stays out of a conversation.
 *
 * The top bar floats: a white card over the thread rather than a strip above it, so the newest
 * messages have the whole height of the window and the older ones scroll away underneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageThreadScreen(
    state: MessageThreadUiState,
    draftState: TextFieldState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    /** The other side's space. The title block is the handle onto it; see the top bar. */
    onOpenSpace: () -> Unit,
    onRetryLoad: () -> Unit,
    onToggleMarkdown: () -> Unit,
    onSend: () -> Unit,
    onRetrySend: (String) -> Unit,
    /** 引用: holds the bubble over the message bar. See `MessageThreadViewModel.quote`. */
    onQuote: (MessageBubble) -> Unit,
    /** The ✕ on a quote card, by bubble id. */
    onRemoveQuote: (String) -> Unit,
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
    var customizing by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(ComposerPanel.NONE) }
    // Turning MD off takes the formatting tiles away, and neither the emoji panel nor the sheet it
    // arranges may be left behind with no key on screen to close them.
    LaunchedEffect(state.isMarkdown) {
        if (!state.isMarkdown) {
            if (panel == ComposerPanel.EMOJI) panel = ComposerPanel.TOOLS
            customizing = false
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            ThreadTopBar(
                state = state,
                showBackButton = showBackButton,
                onBack = onBack,
                onOpenSpace = onOpenSpace,
                onOpenBrowser = { onOpenBrowser(webUrl) },
            )
        },
    ) { padding ->
        // The bar floats, so its height is not taken off the top of the body: the thread runs up
        // underneath it and only the list's own content padding keeps the oldest message clear.
        // The bottom is taken as `paddingWithKeyboard` takes it, for the reason given there.
        val topClearance = padding.calculateTopPadding()
        Column(
            Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize(),
        ) {
            // fillMaxWidth, or the box is only as wide as whatever is inside it — which for the
            // empty state is one line of text, and centring inside that put it against the left edge.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.messages.isEmpty() ->
                        LoadingState(Modifier.padding(top = topClearance))

                    state.error != null && state.messages.isEmpty() ->
                        Box(Modifier.padding(top = topClearance)) {
                            SiteErrorState(
                                error = state.error,
                                onRetry = onRetryLoad,
                                // SiteErrorState picks the recovery per error, which is the whole
                                // reason it takes all three.
                                onOpenBrowser = { onVerify(state.error.webViewUrl(NodeSeekSite.BASE_URL)) },
                                onVerify = onVerify,
                                onSignIn = onSignIn,
                            )
                        }

                    state.messages.isEmpty() ->
                        Text(
                            stringResource(Res.string.message_thread_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = Spacing.xl),
                        )

                    else -> MessageBubbles(state, topClearance, onLinkClick, onRetrySend, onQuote)
                }
            }
            MessageComposer(
                draftState = draftState,
                state = state,
                panel = panel,
                onPanelChange = { panel = it },
                onToggleMarkdown = onToggleMarkdown,
                onSend = onSend,
                onRemoveQuote = onRemoveQuote,
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

/**
 * 3d's floating title card: back, the other side, and the overflow.
 *
 * A Material [TopAppBar] inside the card rather than a hand-laid row, so the slots, the 64dp height
 * and the title's alignment are the component's; the card only supplies the white, the corners and
 * the shadow, and takes the status bar off the top itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadTopBar(
    state: MessageThreadUiState,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onOpenSpace: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    val layers = LocalPlazaLayers.current
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = LayerPageGutter, vertical = 4.dp),
    ) {
        Surface(
            shape = TOP_BAR_SHAPE,
            color = layers.raised,
            border = layers.cardBorder?.let { BorderStroke(1.dp, it) },
            modifier = Modifier.fillMaxWidth().floatShadow(TOP_BAR_SHAPE, layers.shadows),
        ) {
            TopAppBar(
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = layers.raised),
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.action_back),
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        // The whole title opens the space, not the avatar alone: a 32dp square is
                        // the smallest thing on the bar, and the name and the UID beside it are the
                        // same person — tapping either should not do two different things. The
                        // padding sits inside the clickable so it is target, not dead space.
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(Spacing.md))
                            .clickable(
                                onClickLabel = stringResource(Res.string.message_thread_open_space),
                            ) { onOpenSpace() }
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    ) {
                        UserAvatar(url = state.avatarUrl, name = state.userName, size = 32.dp)
                        Column {
                            Text(
                                // Blank until the thread loads when it was opened by a link, which
                                // names a uid and nothing else; every other way in knows the name.
                                if (state.userName.isBlank()) {
                                    stringResource(Res.string.message_thread_title_unknown)
                                } else {
                                    stringResource(Res.string.message_thread_title, state.userName)
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                state.level?.let {
                                    stringResource(Res.string.message_thread_subtitle_level, state.uid, it)
                                } ?: stringResource(Res.string.message_thread_subtitle, state.uid),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = { ThreadMenu(onOpenBrowser = onOpenBrowser) },
            )
        }
    }
}

@Composable
private fun MessageBubbles(
    state: MessageThreadUiState,
    topClearance: Dp,
    onOpenBrowser: (String) -> Unit,
    onRetrySend: (String) -> Unit,
    onQuote: (MessageBubble) -> Unit,
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
        modifier = Modifier.fillMaxSize(),
        // The top is the floating bar's height and a little more, so the oldest message can be
        // scrolled clear of the card rather than parked under it.
        contentPadding =
        PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = topClearance + Spacing.sm, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(newestFirst, key = ThreadRow::key) { row ->
            when (row) {
                is ThreadRow.Day -> DayDivider(row.label)

                is ThreadRow.Bubble ->
                    MessageBubbleRow(
                        message = row.message,
                        onOpenBrowser = onOpenBrowser,
                        onRetrySend = { onRetrySend(row.message.id) },
                        onQuote = { onQuote(row.message) },
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

/** 3d's date pill: a step up from the page, so it reads as a label on it rather than as a bubble. */
@Composable
private fun DayDivider(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .heightIn(min = 24.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * One message, and the line under it.
 *
 * The other side's bubbles are cards — white, with the card shadow — and mine are
 * `primaryContainer`: 3d tells the two apart by colour first and by side second, which is what makes
 * a long run of one person's messages still read at a glance. A message that failed to send is drawn
 * faded, so the eye goes to the red line under it rather than to its text.
 */
@Composable
private fun MessageBubbleRow(
    message: MessageBubble,
    onOpenBrowser: (String) -> Unit,
    onRetrySend: () -> Unit,
    onQuote: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val layers = LocalPlazaLayers.current
    val shape = if (message.isMine) MINE_SHAPE else THEIRS_SHAPE
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxWidth(BUBBLE_MAX_WIDTH)
                .wrapContentWidthTo(message.isMine)
                .then(if (message.isMine) Modifier else Modifier.cardShadow(shape, layers.shadows))
                .clip(shape)
                .background(if (message.isMine) MaterialTheme.colorScheme.primaryContainer else layers.card)
                .then(
                    layers.cardBorder
                        ?.takeUnless { message.isMine }
                        ?.let { Modifier.border(1.dp, it, shape) }
                        ?: Modifier,
                ).combinedClickable(
                    // Nothing on a plain tap: the bubble is not a destination, and the whole point of
                    // the gesture is that it is the *long* press. It goes through `combinedClickable`
                    // rather than a raw `pointerInput` for what that brings with it — the ripple that
                    // says the press registered, and a long-click action TalkBack can announce and
                    // perform, which a hand-rolled detector gives a screen reader no way to reach.
                    onClick = {},
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = stringResource(Res.string.message_bubble_actions),
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
            val contentModifier = if (message.status == SendStatus.FAILED) Modifier.alpha(FAILED_ALPHA) else Modifier
            if (message.isMarkdown) {
                PostRichContent(
                    // With the resolver, because 私信 arrive as the source that was typed: the
                    // server expands `:ac01:` in its own renderer and hands the API the shortcode
                    // untouched. Without it the emoji panel could send a sticker the thread then
                    // showed back as six characters of punctuation.
                    nodes = parseMarkdown(message.content, NodeSeekStickers::urlFor),
                    onLinkClick = onOpenBrowser,
                    onImageClick = onOpenBrowser,
                    textStyle = textStyle,
                    // The long press belongs to the menu below. Left selectable, the renderer's own
                    // `SelectionContainer` would take it first and only on the Markdown bubbles —
                    // which is how a conversation came to offer 复制 on one side and nothing on the
                    // other, the sender's MD switch deciding it without anyone having chosen that.
                    selectable = false,
                    modifier = contentModifier,
                )
            } else {
                Text(message.content, style = textStyle, modifier = contentModifier)
            }
            BubbleMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                content = message.content,
                onQuote = onQuote,
            )
        }
        MessageStatusLine(message = message, onRetrySend = onRetrySend)
    }
}

/**
 * 复制 and 引用, on the long press: 3e's white menu card under the bubble.
 *
 * A Material [DropdownMenu] in the redesign's colours. This was an inverse-surface pill above the
 * bubble for a while, because the old grey menu hung off the bubble like the overflow of a screen
 * that was not there; 3e asks for the menu again, as a raised white card with an icon per action,
 * and the menu already knows how to sit under its anchor, flip above it near the bottom of the
 * window, and close on back or on a tap elsewhere.
 *
 * Copies the message's source rather than what is on screen: a bubble that renders `[看这个](/post-1-1)`
 * as one blue word is a bubble whose link survives only in the Markdown, and 引用 quotes that same
 * string — pasting one and quoting the other would be two different messages.
 */
@Composable
private fun BubbleMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: String,
    onQuote: () -> Unit,
) {
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.message_copied)
    val layers = LocalPlazaLayers.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MENU_SHAPE,
        containerColor = layers.raised,
        // Paper draws no shadow; the outline is what separates the card from the thread there.
        shadowElevation = if (layers.shadows) 8.dp else 0.dp,
        border = layers.cardBorder?.let { BorderStroke(1.dp, it) },
        modifier = Modifier.widthIn(min = MENU_MIN_WIDTH),
    ) {
        BubbleMenuItem(
            icon = PlazaIcons.ContentCopy,
            label = stringResource(Res.string.action_copy),
            onClick = {
                copy("message", content, copied)
                onDismiss()
            },
        )
        BubbleMenuItem(
            icon = PlazaIcons.FormatQuote,
            label = stringResource(Res.string.message_quote_action),
            onClick = {
                onDismiss()
                onQuote()
            },
        )
    }
}

@Composable
private fun BubbleMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/**
 * The line under a bubble: its clock, and for mine what became of it (3d).
 *
 * `21:34 · 已送达`, `10:52 · 发送中` with a clock face, or the failure in the error colour with 重试
 * after it. The other side's bubbles only ever carry the time.
 */
@Composable
private fun MessageStatusLine(
    message: MessageBubble,
    onRetrySend: () -> Unit,
) {
    val clock = message.sentAtMillis?.let(TimeFormat::clock) ?: message.sentAtText
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (message.status) {
            SendStatus.SENDING -> {
                val sending = stringResource(Res.string.message_status_sending)
                Text(
                    text = clock?.let { stringResource(Res.string.notification_time_pair, it, sending) } ?: sending,
                    style = style,
                    color = muted,
                )
                Icon(PlazaIcons.Schedule, contentDescription = null, tint = muted, modifier = Modifier.size(13.dp))
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
                    text = message.failureReason ?: stringResource(Res.string.message_status_failed),
                    style = style,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(text = "·", style = style, color = MaterialTheme.colorScheme.error)
                Text(
                    text = stringResource(Res.string.action_retry),
                    style = style.copy(fontWeight = FontWeight.SemiBold),
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
                    message.isMine && clock != null ->
                        Text(stringResource(Res.string.message_status_sent, clock), style = style, color = muted)

                    clock != null -> Text(clock, style = style, color = muted)
                }
        }
    }
}

/** What stands in the keyboard's place under the message bar. */
internal enum class ComposerPanel { NONE, TOOLS, EMOJI }

/**
 * Everything under the thread: uploads in flight, the quote cards, the message bar, and the panel
 * that takes the keyboard's place when the + key is on (3d, 3e).
 *
 * The + key replaces the formatting strip this bar used to carry above itself. 3e folds the strip,
 * the MD switch, the photo picker and the emoji key into one grid of tiles behind it, so the bar at
 * rest is three things — +, the field, send — and the conversation keeps the height the strip took.
 * The grid is still the strip underneath: its keys are the arranged ones (`state.toolbar`), the
 * wrench is its last tile, and with MD off the formatting tiles are absent rather than disabled —
 * the server takes the text verbatim then, and a key that inserted `**` would be offering syntax
 * that arrives as literal asterisks.
 */
@Composable
private fun MessageComposer(
    draftState: TextFieldState,
    state: MessageThreadUiState,
    panel: ComposerPanel,
    onPanelChange: (ComposerPanel) -> Unit,
    onToggleMarkdown: () -> Unit,
    onSend: () -> Unit,
    onRemoveQuote: (String) -> Unit,
    onPickImages: (List<PickedImage>) -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onCustomize: () -> Unit,
) {
    val pickImages =
        rememberImagePicker(
            maxItems = MAX_IMAGES_PER_PICK,
            fallbackName = stringResource(Res.string.composer_image_default_name),
            onPicked = onPickImages,
        )
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    var recentEmoji by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // The panel stands in for the keyboard, and a keyboard is the one thing back is always expected
    // to dismiss before it leaves the screen.
    PlazaBackHandler(enabled = panel != ComposerPanel.NONE) { onPanelChange(ComposerPanel.NONE) }

    val openPanel: (ComposerPanel) -> Unit = { next ->
        // Focus goes with the keyboard: a field that kept it would bring the keyboard straight back
        // up over the panel on the next recomposition, and tapping the field is how the panel closes.
        focusManager.clearFocus()
        keyboard?.hide()
        onPanelChange(next)
    }

    Column(Modifier.fillMaxWidth()) {
        // Outside the MD branch: an upload started before the toggle was flipped is still running,
        // and its cell is the only place the user can see that, or cancel it.
        AttachmentTray(
            attachments = state.attachments,
            onRemove = onRemoveAttachment,
            onRetry = onRetryAttachment,
        )
        // One quoted message waiting over the bar (3e): whose words, the first line of them, and a
        // ✕. The excerpt goes through the conversation list's [previewText], so a quoted picture or
        // sticker reads as [图片] or [表情] rather than as the Markdown that carries it.
        state.quotes.forEach { quoted ->
            QuotePreview(
                title =
                if (quoted.isMine) {
                    stringResource(Res.string.message_quote_chip_mine)
                } else {
                    stringResource(Res.string.message_quote_chip, state.userName)
                },
                excerpt = previewText(contentPreview(quoted.content)),
                titleColor = MaterialTheme.colorScheme.primary,
                leading = {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                },
                onRemove = { onRemoveQuote(quoted.id) },
                removeLabel = stringResource(Res.string.message_quote_remove),
                modifier = Modifier.padding(start = LayerPageGutter, end = LayerPageGutter, top = Spacing.xs),
            )
        }
        MessageInputBar(
            draftState = draftState,
            isMarkdown = state.isMarkdown,
            canSend = state.canSend,
            panelOpen = panel != ComposerPanel.NONE,
            onTogglePanel = {
                if (panel == ComposerPanel.NONE) openPanel(ComposerPanel.TOOLS) else onPanelChange(ComposerPanel.NONE)
            },
            fieldFocus = fieldFocus,
            onFieldFocused = { onPanelChange(ComposerPanel.NONE) },
            onSend = onSend,
        )
        when (panel) {
            ComposerPanel.NONE -> Unit

            ComposerPanel.TOOLS ->
                ToolGrid(
                    state = state,
                    onToggleMarkdown = onToggleMarkdown,
                    onAction = { action ->
                        when (action) {
                            EditorAction.IMAGE -> pickImages()

                            EditorAction.EMOJI -> onPanelChange(ComposerPanel.EMOJI)

                            else -> {
                                draftState.edit { applyMarkdown(action) }
                                // Straight back to the field: the placeholder the key inserted is
                                // selected, and the next keystroke is meant to replace it.
                                onPanelChange(ComposerPanel.NONE)
                                fieldFocus.requestFocus()
                            }
                        }
                    },
                    onCustomize = onCustomize,
                )

            ComposerPanel.EMOJI ->
                NodeSeekEmojiPanel(
                    onInsert = { text -> draftState.edit { insertText(text) } },
                    onBackspace = { draftState.edit { deleteBackwards() } },
                    recent = recentEmoji,
                    onRecentChange = { recentEmoji = it },
                )
        }
    }
}

/**
 * The message bar: +, one pill for the text, and the send key, sitting on its bottom line (3d).
 *
 * Built out of a `BasicTextField` and a `Surface` rather than a filled `TextField`, because a filled
 * field reserves the room a floating label would need and is 56dp tall before it holds anything —
 * next to a 48dp send key that read as three mismatched blocks. The field grows with the draft up to
 * [MAX_INPUT_LINES] and the two keys stay on the last line, which is what makes the row settle.
 */
@Composable
private fun MessageInputBar(
    draftState: TextFieldState,
    isMarkdown: Boolean,
    canSend: Boolean,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
    fieldFocus: FocusRequester,
    onFieldFocused: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(start = LayerPageGutter, end = LayerPageGutter, top = Spacing.sm, bottom = Spacing.md),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // A toggle, so the key says which way it will go: + while the grid is away, ✕ on a tonal
        // disc while it is up, as 3e draws it.
        IconToggleButton(
            checked = panelOpen,
            onCheckedChange = { onTogglePanel() },
            colors =
            IconButtonDefaults.iconToggleButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.size(INPUT_CONTROL_SIZE),
        ) {
            Icon(
                if (panelOpen) Icons.Default.Close else Icons.Default.Add,
                contentDescription = stringResource(Res.string.message_tools),
            )
        }
        MessageDraftField(
            draftState = draftState,
            isMarkdown = isMarkdown,
            modifier =
            Modifier
                .weight(1f)
                .focusRequester(fieldFocus)
                .onFocusChanged { if (it.isFocused) onFieldFocused() },
        )
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            colors =
            IconButtonDefaults.filledIconButtonColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.size(INPUT_CONTROL_SIZE),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(Res.string.message_send),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MessageDraftField(
    draftState: TextFieldState,
    isMarkdown: Boolean,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    EditorTextField(
        state = draftState,
        hint =
        stringResource(
            if (isMarkdown) Res.string.message_input_hint_markdown else Res.string.message_input_hint_plain,
        ),
        textStyle =
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        // One line of placeholder, elided: the pill is 48dp tall when empty and a wrapping hint
        // would grow it before anything has been typed.
        hintMaxLines = 1,
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_INPUT_LINES),
        modifier = modifier,
        container = { content ->
            // A white pill on the page, lifted like a card: 3d draws the field as the one surface
            // on the bar, with the two keys either side of it sitting on the page itself.
            Surface(
                shape = RoundedCornerShape(INPUT_CONTROL_SIZE / 2),
                color = layers.raised,
                border = layers.cardBorder?.let { BorderStroke(1.dp, it) },
                modifier = Modifier.cardShadow(RoundedCornerShape(INPUT_CONTROL_SIZE / 2), layers.shadows),
            ) {
                Box(
                    modifier =
                    Modifier
                        .heightIn(min = INPUT_CONTROL_SIZE)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    content()
                }
            }
        },
    )
}

/**
 * 3e's grid behind the + key, four tiles to a row.
 *
 * The keys that open something — 相册 and 表情 — lead, then the MD switch, then the formatting keys
 * in the order the wrench arranged them, then the wrench itself. With MD off only the switch is left:
 * the rest would all be inserting Markdown into a message the server will take verbatim.
 *
 * 3e also draws a 拍照 tile. The composers pick images through the platform photo picker and nothing
 * else, so there is no camera for a tile to open; it is left out rather than drawn as a dead key.
 */
@Composable
private fun ToolGrid(
    state: MessageThreadUiState,
    onToggleMarkdown: () -> Unit,
    onAction: (EditorAction) -> Unit,
    onCustomize: () -> Unit,
) {
    val tiles = buildList<@Composable () -> Unit> {
        if (state.isMarkdown) {
            state.toolbar.enabled.filter { it == EditorAction.IMAGE || it == EditorAction.EMOJI }.forEach { action ->
                add {
                    ToolTile(
                        icon = if (action == EditorAction.IMAGE) PlazaIcons.PhotoLibrary else action.icon,
                        label = stringResource(action.label),
                        onClick = { onAction(action) },
                    )
                }
            }
        }
        add {
            ToolTile(
                icon = PlazaIcons.Markdown,
                label =
                stringResource(
                    if (state.isMarkdown) Res.string.message_tool_markdown_on else Res.string.message_tool_markdown_off,
                ),
                selected = state.isMarkdown,
                toggleLabel = stringResource(Res.string.message_markdown_toggle),
                onClick = onToggleMarkdown,
            )
        }
        if (state.isMarkdown) {
            state.toolbar.enabled.filterNot { it == EditorAction.IMAGE || it == EditorAction.EMOJI }.forEach { action ->
                add {
                    ToolTile(icon = action.icon, label = stringResource(action.label), onClick = { onAction(action) })
                }
            }
            add {
                ToolTile(
                    icon = Icons.Default.Build,
                    label = stringResource(Res.string.message_tool_customize),
                    onClick = onCustomize,
                )
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // A flow of quarters rather than a lazy grid: there are never more than a dozen tiles,
            // and a lazy grid inside this column would need a fixed height to measure at all.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = TOOL_COLUMNS,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                tiles.forEach { tile ->
                    Box(Modifier.fillMaxWidth(1f / TOOL_COLUMNS), contentAlignment = Alignment.TopCenter) { tile() }
                }
            }
        }
    }
}

/**
 * One tile of the grid: a 60dp rounded square with its name under it.
 *
 * [selected] makes it a switch — the MD tile is the site's MD On/Off, and a screen reader has to hear
 * it as one, named by [toggleLabel] rather than by the 开/关 its caption changes between.
 */
@Composable
private fun ToolTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean? = null,
    toggleLabel: String? = null,
) {
    val layers = LocalPlazaLayers.current
    val on = selected == true
    val interaction =
        if (selected != null) {
            Modifier.toggleable(value = selected, role = Role.Switch, onValueChange = { onClick() })
        } else {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        }
    // The whole cell is the target, not only the square, so the caption under it is one too.
    Column(
        modifier = Modifier.fillMaxWidth().then(interaction),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier =
            Modifier
                .size(TOOL_TILE_SIZE)
                .cardShadow(TOOL_TILE_SHAPE, layers.shadows)
                .clip(TOOL_TILE_SHAPE)
                .background(if (on) MaterialTheme.colorScheme.primary else layers.raised)
                .then(layers.cardBorder?.let { Modifier.border(1.dp, it, TOOL_TILE_SHAPE) } ?: Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = toggleLabel,
                tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            // Unbounded, so a caption a little wider than its quarter of the row spills into the gap
            // between tiles instead of being cut: four 60dp tiles leave 20dp of air between them.
            softWrap = false,
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}

@Composable
private fun ThreadMenu(onOpenBrowser: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val layers = LocalPlazaLayers.current
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.action_more))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MENU_SHAPE,
            containerColor = layers.raised,
            shadowElevation = if (layers.shadows) 8.dp else 0.dp,
            border = layers.cardBorder?.let { BorderStroke(1.dp, it) },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_open_in_browser)) },
                onClick = {
                    expanded = false
                    onOpenBrowser()
                },
            )
        }
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

/** 3d's bubble corners: 20dp all round but the one pointing at its sender, which tucks in to 6dp. */
private val THEIRS_SHAPE: Shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
private val MINE_SHAPE: Shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)

/** How far a failed message fades, so the red line under it is what the eye lands on. */
private const val FAILED_ALPHA = 0.72f

private val TOP_BAR_SHAPE: Shape = RoundedCornerShape(24.dp)
private val MENU_SHAPE: Shape = RoundedCornerShape(20.dp)
private val MENU_MIN_WIDTH = 200.dp
private const val TOOL_COLUMNS = 4
private val TOOL_TILE_SIZE = 60.dp
private val TOOL_TILE_RADIUS = 20.dp
private val TOOL_TILE_SHAPE: Shape = RoundedCornerShape(TOOL_TILE_RADIUS)
private const val MAX_INPUT_LINES = 5
private const val MAX_IMAGES_PER_PICK = 9

/** One height for +, the send key and the empty draft pill, so the bar reads as a row. */
private val INPUT_CONTROL_SIZE = 48.dp

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
            onOpenSpace = {},
            onRetryLoad = {},
            onToggleMarkdown = {},
            onSend = {},
            onRetrySend = {},
            onQuote = {},
            onRemoveQuote = {},
            onPickImages = {},
            onRemoveAttachment = {},
            onRetryAttachment = {},
            onToolbarChange = {},
            onToolbarReset = {},
        )
    }
}
