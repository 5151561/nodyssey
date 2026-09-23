package io.github.nodyssey.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.contentPreview
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.common.siteErrorRecovery
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.messages_empty
import io.github.nodyssey.ui.resources.messages_new_conversation
import io.github.nodyssey.ui.resources.messages_new_conversation_empty
import io.github.nodyssey.ui.resources.messages_new_conversation_hint
import io.github.nodyssey.ui.resources.messages_new_conversation_intro
import io.github.nodyssey.ui.resources.messages_pinned
import io.github.nodyssey.ui.resources.messages_section_all
import io.github.nodyssey.ui.resources.messages_snippet_mine_prefix
import io.github.nodyssey.ui.resources.unread_count_capped
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.AvatarShape
import io.github.plaza.designsys.component.GroupDividerInset
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.listAvatarSize
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.floatShadow
import org.jetbrains.compose.resources.stringResource

/**
 * Board 7e, redrawn as 5b — the 私信 group of the notification tab.
 *
 * 系统通知 is pinned at the top by [io.github.nodyssey.data.NetworkMessageRepository]; it is an
 * ordinary conversation, drawn without an avatar because it has no member behind it. 5b gives it a
 * card of its own above the rest, under a 全部私信 heading: it is the site talking, not a member, and
 * sharing one card made it read as the first of the reader's own conversations.
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
    onSignIn: () -> Unit,
    /** Clears a Cloudflare challenge; the sheet's search is the one thing here that can hit one. */
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Box(modifier.fillMaxSize()) {
        if (state.conversations.isEmpty()) {
            Text(
                text = stringResource(Res.string.messages_empty),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            // fillMaxSize, or the list is only as tall as the rows in it — and 私信 is a short list.
            // Everything below the last row would then be a dead strip that dispatches no scroll,
            // so a pull down there could neither bring 单手模式's title back nor reach the refresh.
            val (pinned, others) = state.conversations.partition(MessageConversation::isSystem)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding =
                PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = 12.dp, bottom = FAB_CLEARANCE),
            ) {
                // Each pinned conversation is a card on its own, so a second one — which the site
                // has never sent — would still not be drawn as the first row of the member list.
                items(pinned, key = MessageConversation::uid) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        first = true,
                        last = true,
                        nowMillis = state.nowMillis,
                        onClick = { onConversationClick(conversation) },
                    )
                }
                if (others.isNotEmpty()) {
                    // The heading only earns its place when there is a pinned card above to set the
                    // members apart from; on its own it would be labelling the whole page.
                    if (pinned.isNotEmpty()) {
                        item(key = "all-heading") {
                            ListGroupLabel(stringResource(Res.string.messages_section_all))
                        }
                    }
                    itemsIndexed(others, key = { _, conversation -> conversation.uid }) { index, conversation ->
                        ConversationRow(
                            conversation = conversation,
                            first = index == 0,
                            last = index == others.lastIndex,
                            nowMillis = state.nowMillis,
                            onClick = { onConversationClick(conversation) },
                        )
                    }
                }
            }
        }

        // The content overload rather than `icon`/`text`: that one wraps its label in an animation
        // container that does not surface the text to semantics, and the button would announce
        // itself unnamed. Its own elevation is off because the float shadow draws the lift in the
        // page's hue, as every floating control in the redesign does.
        val fabShape = FloatingActionButtonDefaults.extendedFabShape
        ExtendedFloatingActionButton(
            onClick = onNewConversation,
            shape = fabShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg)
                .floatShadow(fabShape, LocalPlazaLayers.current.shadows),
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.messages_new_conversation),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
            onSignIn = onSignIn,
            onVerify = onVerify,
        )
    }
}

/**
 * One conversation, as one slice of its card (5b).
 *
 * Unread shows in the name's weight, a primary time stamp and the count badge; the row itself is no
 * longer tinted, which on a white card read as a selection rather than as news.
 */
@Composable
private fun ConversationRow(
    conversation: MessageConversation,
    first: Boolean,
    last: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUnread = conversation.unreadCount > 0
    GroupedListItem(
        first = first,
        last = last,
        onClick = onClick,
        modifier = modifier,
        leadingContent = { ConversationAvatar(conversation) },
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.userName,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.isSystem) {
                    Icon(
                        PlazaIcons.PushPin,
                        contentDescription = stringResource(Res.string.messages_pinned),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        },
        supportingContent = {
            Text(text = conversationSnippet(conversation), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        // The stamp over the count, as one column at the row's end, so every row's time lines up
        // against the same edge whatever the name beside it.
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                conversationStamp(conversation, nowMillis)?.let { stamp ->
                    Text(
                        text = stamp,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                        // The time is where 5b puts the first sign of news, ahead of the badge.
                        color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (isUnread) {
                    // Badge's own error red, the same mark the group tabs and the tab bar carry.
                    Badge { Text(unreadLabel(conversation.unreadCount, MAX_UNREAD)) }
                }
            }
        },
        dividerInset = GroupDividerInset + CONVERSATION_AVATAR + GroupDividerInset,
    )
}

@Composable
private fun ConversationAvatar(conversation: MessageConversation) {
    if (conversation.isSystem) {
        // 5b's megaphone rather than the bell this used to be: the bell is the tab's own icon,
        // and a conversation wearing it read as a shortcut back to the tab it was already on.
        Box(
            Modifier
                .size(CONVERSATION_AVATAR)
                .clip(AvatarShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PlazaIcons.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    } else {
        UserAvatar(url = conversation.avatarUrl, name = conversation.userName, size = CONVERSATION_AVATAR)
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
 * The last message as one line: the Markdown flattened away, the pictures named — see [previewText].
 *
 * Only the words survive. Links keep their text and lose their target, which is the one the
 * conversation opens onto anyway; the system conversation, which is all links, reads as the
 * sentence it was written as instead of as its own markup.
 */
@Composable
private fun conversationSnippet(conversation: MessageConversation): String {
    val body = previewText(conversation.snippet).orEmpty()
    if (!conversation.isSnippetMine) return body
    return stringResource(Res.string.messages_snippet_mine_prefix) + body
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationSheet(
    state: NewConversationState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    onRecipientClick: (UserSearchResult) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
        containerColor = LocalPlazaLayers.current.page,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(Res.string.messages_new_conversation),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(Res.string.messages_new_conversation_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text(stringResource(Res.string.messages_new_conversation_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                state.isSearching -> PlazaSpinner(Modifier.align(Alignment.CenterHorizontally).describedAsLoading())

                // Before the empty case: a search that never reached the server has not found
                // "no such user", and telling the user it did sends them off renaming their query.
                //
                // The sentence carries its own way out. 用户搜索 goes through the site like anything
                // else, so it meets the same Cloudflare wall — and a bare red line left the reader
                // retyping a name that was never the problem.
                state.error != null -> {
                    val recovery =
                        siteErrorRecovery(
                            error = state.error,
                            onVerify = onVerify,
                            onSignIn = onSignIn,
                            onRetry = onSearch,
                        )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.error.shortMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        recovery?.let {
                            TextButton(onClick = it.onClick) {
                                Text(it.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                state.results.isEmpty() && state.query.isNotBlank() ->
                    Text(
                        stringResource(Res.string.messages_new_conversation_empty),
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
                                UserAvatar(url = user.avatarUrl, name = user.name, size = listAvatarSize())
                                Text(user.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
            }
        }
    }
}

private val FAB_CLEARANCE = 96.dp

/** 5b's measurements: 72dp rows, a 44dp avatar. */
private val CONVERSATION_AVATAR = 44.dp
private const val MAX_UNREAD = 99

/**
 * An unread count, capped, with the cap made visible.
 *
 * Clamping alone renders 150 unread as a bare "99", which reads as an exact figure rather than as
 * "more than we will draw". Shared by the conversation badges and the group tabs so the two
 * cannot disagree about what a capped count looks like.
 */
@Composable
internal fun unreadLabel(count: Int, cap: Int): String =
    if (count > cap) stringResource(Res.string.unread_count_capped, cap) else count.toString()

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ConversationListPreview() {
    PlazaTheme {
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
                        snippet = contentPreview("您的[评论](/post-1-1)被用户[iwil](/space/4471)投喂鸡腿"),
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
                        snippet = contentPreview("改名的事我问过管理，说要等 UID 显示上线"),
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
                        snippet = contentPreview("好的，我先转账，晚点把 push 链接发我"),
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
            onSignIn = {},
            onVerify = {},
        )
    }
}
