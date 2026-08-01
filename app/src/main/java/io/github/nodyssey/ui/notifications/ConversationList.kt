package io.github.nodyssey.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.core.TimeFormat
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.model.InlineNode
import io.github.nodyssey.model.RichNode
import io.github.nodyssey.ui.common.AvatarShape
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.UserAvatar
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.composer.parseMarkdown
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES

/**
 * Board 7e — the 私信 group of the notification tab.
 *
 * 系统通知 is pinned at the top by [io.github.nodyssey.data.NetworkMessageRepository]; it is an
 * ordinary conversation whose messages happen to be Markdown, which is why its snippet renders as
 * styled text while everyone else's is plain.
 */
@Composable
internal fun ConversationList(
    state: NotificationsUiState,
    onConversationClick: (MessageConversation) -> Unit,
    onNewConversation: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    onRecipientClick: (UserSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (state.conversations.isEmpty()) {
            Text(
                text = stringResource(R.string.messages_empty),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
                items(state.conversations, key = MessageConversation::uid) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        nowMillis = state.nowMillis,
                        onClick = { onConversationClick(conversation) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNewConversation,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
        ) {
            Icon(
                NodysseyIcons.AddComment,
                contentDescription = stringResource(R.string.messages_new_conversation),
            )
        }
    }

    if (state.newConversation.isVisible) {
        NewConversationSheet(
            state = state.newConversation,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onDismiss = onDismiss,
            onRecipientClick = onRecipientClick,
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: MessageConversation,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val isUnread = conversation.unreadCount > 0
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(
                if (isUnread) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ).clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conversation.isSystem) {
            Box(
                Modifier
                    .size(AVATAR)
                    .clip(AvatarShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        } else {
            UserAvatar(
                url = conversation.avatarUrl,
                name = conversation.userName,
                size = AVATAR,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                /*
                 * The name and its pin travel together inside one weighted row, and the stamp is the
                 * only unweighted child, so it sits against the right edge of every row alike.
                 *
                 * Weighting the name *and* a spacer instead made the two split the slack evenly,
                 * which parked each row's stamp a different distance in from the edge — a column of
                 * times that visibly failed to line up.
                 */
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.userName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conversation.isSystem) {
                        Icon(
                            NodysseyIcons.PushPin,
                            contentDescription = stringResource(R.string.messages_pinned),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                conversationStamp(conversation, nowMillis)?.let { stamp ->
                    Text(
                        text = stamp,
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = TABULAR_FIGURES,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversationSnippet(conversation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isUnread) {
                    // `primary`, not Badge's default `error`: an unread message is a thing to read,
                    // not a thing that went wrong.
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(unreadLabel(conversation.unreadCount, MAX_UNREAD))
                    }
                }
            }
        }
    }
}

@Composable
private fun conversationStamp(
    conversation: MessageConversation,
    nowMillis: Long,
): String? =
    conversation.updatedAtMillis
        ?.let { TimeFormat.conversationStamp(it, nowMillis) }
        ?: conversation.updatedAtText

/**
 * The snippet, with the system conversation's Markdown links picked out in the brand colour.
 *
 * Only the link text survives — the row has one line, and the target is whatever the conversation
 * opens onto anyway.
 */
@Composable
private fun conversationSnippet(conversation: MessageConversation): AnnotatedString {
    val emphasis = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    val body =
        if (conversation.isSystem) {
            buildAnnotatedString {
                parseMarkdown(conversation.snippet)
                    .filterIsInstance<RichNode.Paragraph>()
                    .flatMap(RichNode.Paragraph::inlines)
                    .forEach { inline ->
                        when (inline) {
                            is InlineNode.Text -> append(inline.text)
                            is InlineNode.Link -> withStyle(emphasis) { append(inline.text) }
                            else -> Unit
                        }
                    }
            }
        } else {
            AnnotatedString(conversation.snippet)
        }
    if (!conversation.isSnippetMine) return body
    val prefix = stringResource(R.string.messages_snippet_mine_prefix)
    return buildAnnotatedString {
        append(prefix)
        append(body)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationSheet(
    state: NewConversationState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    onRecipientClick: (UserSearchResult) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.messages_new_conversation),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.messages_new_conversation_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text(stringResource(R.string.messages_new_conversation_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                state.isSearching -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

                // Before the empty case: a search that never reached the server has not found
                // "no such user", and telling the user it did sends them off renaming their query.
                state.error != null ->
                    Text(
                        state.error.shortMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                state.results.isEmpty() && state.query.isNotBlank() ->
                    Text(
                        stringResource(R.string.messages_new_conversation_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        items(state.results, key = UserSearchResult::uid) { user ->
                            Row(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onRecipientClick(user) }
                                    .padding(vertical = Spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(url = user.avatarUrl, name = user.name, size = 34.dp)
                                Text(user.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
            }
        }
    }
}

private val AVATAR = 44.dp
private val FAB_CLEARANCE = 88.dp
private const val MAX_UNREAD = 99

/**
 * An unread count, capped, with the cap made visible.
 *
 * Clamping alone renders 150 unread as a bare "99", which reads as an exact figure rather than as
 * "more than we will draw". Shared by the conversation badges and the category chips so the two
 * cannot disagree about what a capped count looks like.
 */
@Composable
internal fun unreadLabel(count: Int, cap: Int): String =
    if (count > cap) stringResource(R.string.unread_count_capped, cap) else count.toString()

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ConversationListPreview() {
    NodysseyTheme {
        ConversationList(
            state =
            NotificationsUiState(
                isSignedIn = true,
                nowMillis = PREVIEW_NOW,
                conversations =
                listOf(
                    MessageConversation(
                        uid = 1,
                        userName = MessageConversation.SYSTEM_NAME,
                        avatarUrl = null,
                        snippet = "您的[评论](/post-1-1)被用户[iwil](/space/4471)投喂鸡腿",
                        isSnippetMine = false,
                        updatedAtMillis = PREVIEW_NOW - 70 * 60_000L,
                        updatedAtText = null,
                        unreadCount = 1,
                        isSystem = true,
                    ),
                    MessageConversation(
                        uid = 2,
                        userName = "nssk",
                        avatarUrl = null,
                        snippet = "改名的事我问过管理，说要等 UID 显示上线",
                        isSnippetMine = false,
                        updatedAtMillis = PREVIEW_NOW - 4 * 60_000L,
                        updatedAtText = null,
                        unreadCount = 2,
                        isSystem = false,
                    ),
                    MessageConversation(
                        uid = 3,
                        userName = "demain",
                        avatarUrl = null,
                        snippet = "好的，我先转账，晚点把 push 链接发我",
                        isSnippetMine = true,
                        updatedAtMillis = PREVIEW_NOW - 26 * 60 * 60_000L,
                        updatedAtText = null,
                        unreadCount = 0,
                        isSystem = false,
                    ),
                ),
            ),
            onConversationClick = {},
            onNewConversation = {},
            onQueryChange = {},
            onSearch = {},
            onDismiss = {},
            onRecipientClick = {},
        )
    }
}
