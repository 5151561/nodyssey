package io.github.nsreader.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.TimeFormat
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.composer.parseMarkdown
import io.github.nsreader.ui.richtext.RichContent
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing

@Composable
fun MessageThreadRoute(
    viewModel: MessageThreadViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MessageThreadScreen(
        state = state,
        onBack = onBack,
        onSignIn = onSignIn,
        onVerify = onVerify,
        onOpenBrowser = onOpenBrowser,
        onRetryLoad = viewModel::refresh,
        onDraftChange = viewModel::updateDraft,
        onToggleMarkdown = viewModel::toggleMarkdown,
        onSend = viewModel::send,
        onRetrySend = viewModel::retry,
        modifier = modifier,
    )
}

/** Board 7f — full screen, so the tab bar stays out of a conversation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageThreadScreen(
    state: MessageThreadUiState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleMarkdown: () -> Unit,
    onSend: () -> Unit,
    onRetrySend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val webUrl = NodeSeekSite.BASE_URL + NodeSeekSite.messageThreadWebPath(state.uid)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading && state.messages.isEmpty() -> LoadingState()

                    state.error != null && state.messages.isEmpty() ->
                        NodeSeekErrorState(
                            error = state.error,
                            onRetry = onRetryLoad,
                            onOpenBrowser = {
                                if (state.error == NodeSeekError.LoginRequired) onSignIn() else onVerify()
                            },
                        )

                    state.messages.isEmpty() ->
                        Text(
                            stringResource(R.string.message_thread_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )

                    else -> MessageBubbles(state, onOpenBrowser, onRetrySend)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MessageInputBar(
                draft = state.draft,
                isMarkdown = state.isMarkdown,
                canSend = state.canSend,
                onDraftChange = onDraftChange,
                onToggleMarkdown = onToggleMarkdown,
                onSend = onSend,
            )
        }
    }
}

@Composable
private fun MessageBubbles(
    state: MessageThreadUiState,
    onOpenBrowser: (String) -> Unit,
    onRetrySend: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.messages.forEachIndexed { index, message ->
            dividerLabel(message, state.messages.getOrNull(index - 1), state.nowMillis)?.let { label ->
                item(key = "divider-${message.id}") { DayDivider(label) }
            }
            item(key = message.id) {
                MessageBubbleRow(
                    message = message,
                    isMarkdown = state.isMarkdown,
                    onOpenBrowser = onOpenBrowser,
                    onRetrySend = { onRetrySend(message.id) },
                )
            }
        }
    }
}

/** A chip appears at the start of the thread and whenever the conversation crosses to a new day. */
private fun dividerLabel(
    message: MessageBubble,
    previous: MessageBubble?,
    nowMillis: Long,
): String? {
    val millis = message.sentAtMillis ?: return null
    val label = TimeFormat.messageDivider(millis, nowMillis)
    val previousLabel =
        previous?.sentAtMillis?.let { TimeFormat.messageDivider(it, nowMillis) } ?: return label
    // The label carries a clock as well as a day, so only the day half decides.
    return label.takeIf { it.substringBefore(' ') != previousLabel.substringBefore(' ') }
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
    isMarkdown: Boolean,
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
            if (isMarkdown) {
                RichContent(
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
                    NodeSeekIcons.ErrorCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    stringResource(R.string.message_status_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onRetrySend).padding(horizontal = 2.dp),
                )
            }

            SendStatus.SENT ->
                when {
                    message.isEdited && clock != null ->
                        Text(
                            stringResource(R.string.message_edited, clock),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInputBar(
    draft: String,
    isMarkdown: Boolean,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onToggleMarkdown: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = Spacing.sm, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarkdownToggle(isMarkdown = isMarkdown, onToggle = onToggleMarkdown)
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = {
                Text(
                    stringResource(
                        if (isMarkdown) {
                            R.string.message_input_hint_markdown
                        } else {
                            R.string.message_input_hint_plain
                        },
                    ),
                )
            },
            maxLines = MAX_INPUT_LINES,
            shape = RoundedCornerShape(22.dp),
            colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (canSend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ).clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                NodeSeekIcons.Send,
                contentDescription = stringResource(R.string.message_send),
                tint =
                if (canSend) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The site's MD On/Off switch, drawn small enough to sit in a message bar.
 *
 * A Material `Switch` is 52×32 before its touch target, which would take the width of two buttons in
 * a row that also has to hold a growing text field.
 */
@Composable
private fun MarkdownToggle(
    isMarkdown: Boolean,
    onToggle: () -> Unit,
) {
    val trackColor =
        if (isMarkdown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    // "MD" alone tells a screen reader nothing, so the switch carries the full name.
    val label = stringResource(R.string.message_markdown_toggle)
    Column(
        modifier =
        Modifier
            .width(48.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .toggleable(
                value = isMarkdown,
                role = Role.Switch,
                onValueChange = { onToggle() },
            ).semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .width(30.dp)
                .height(16.dp)
                .clip(CircleShape)
                .background(trackColor)
                .padding(2.dp),
            contentAlignment = if (isMarkdown) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
        Text(
            text = stringResource(R.string.message_markdown_label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color =
            if (isMarkdown) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun MessageThreadPreview() {
    val now = 1_785_000_000_000L
    NodeSeekTheme {
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
                        isEdited = false,
                        status = SendStatus.SENT,
                    ),
                    MessageBubble(
                        id = "2",
                        isMine = true,
                        content = "那大概什么时候？我这 ID 打错字快两年了",
                        sentAtMillis = now - 37 * 60_000L,
                        sentAtText = null,
                        isEdited = false,
                        status = SendStatus.SENT,
                    ),
                    MessageBubble(
                        id = "3",
                        isMine = false,
                        content = "没给时间点。你可以先在 [求教如何改用户名](/post-1-1) 里顶一下",
                        sentAtMillis = now - 29 * 60_000L,
                        sentAtText = null,
                        isEdited = true,
                        status = SendStatus.SENT,
                    ),
                    MessageBubble(
                        id = "4",
                        isMine = true,
                        content = "行，我发个投票试试",
                        sentAtMillis = now - 60_000L,
                        sentAtText = null,
                        isEdited = false,
                        status = SendStatus.SENDING,
                    ),
                    MessageBubble(
                        id = "5",
                        isMine = true,
                        content = "顺便问下星辰能转账吗",
                        sentAtMillis = now,
                        sentAtText = null,
                        isEdited = false,
                        status = SendStatus.FAILED,
                    ),
                ),
            ),
            onBack = {},
            onSignIn = {},
            onVerify = {},
            onOpenBrowser = {},
            onRetryLoad = {},
            onDraftChange = {},
            onToggleMarkdown = {},
            onSend = {},
            onRetrySend = {},
        )
    }
}
