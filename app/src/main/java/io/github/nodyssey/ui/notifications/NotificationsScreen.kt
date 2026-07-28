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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.TimeFormat
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.ui.common.LoadingState
import io.github.nodyssey.ui.common.NodeSeekErrorState
import io.github.nodyssey.ui.common.SignedOutState
import io.github.nodyssey.ui.common.UserAvatar
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Sizes
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES
import io.github.nodyssey.ui.theme.readableWidth

@Composable
fun NotificationsRoute(
    viewModel: NotificationsViewModel,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onNotificationClick: (ForumNotification) -> Unit,
    onOpenThread: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationsScreen(
        state = state,
        onSignIn = onSignIn,
        onVerify = onVerify,
        onCategoryChange = viewModel::selectCategory,
        onRetry = viewModel::refresh,
        onMarkAllRead = viewModel::markAllRead,
        onNotificationClick = {
            viewModel.markOpened(it.id)
            onNotificationClick(it)
        },
        onConversationClick = { conversation ->
            viewModel.markConversationOpened(conversation.uid)
            onOpenThread(conversation.uid, conversation.userName)
        },
        onNewConversation = viewModel::showNewConversation,
        onNewConversationQueryChange = viewModel::updateNewConversationQuery,
        onNewConversationSearch = viewModel::searchRecipients,
        onNewConversationDismiss = viewModel::dismissNewConversation,
        onRecipientClick = { user ->
            viewModel.dismissNewConversation()
            onOpenThread(user.uid, user.name)
        },
        modifier = modifier,
    )
}

/**
 * Boards 7d and 7e.
 *
 * One screen rather than two: 私信 is a *group* of the same 通知 tab on the site, so it keeps the
 * title, the 全部已读 action and the group chips and swaps only the list underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onCategoryChange: (NotificationCategory) -> Unit,
    onRetry: () -> Unit,
    onMarkAllRead: () -> Unit,
    onNotificationClick: (ForumNotification) -> Unit,
    onConversationClick: (MessageConversation) -> Unit,
    onNewConversation: () -> Unit,
    onNewConversationQueryChange: (String) -> Unit,
    onNewConversationSearch: () -> Unit,
    onNewConversationDismiss: () -> Unit,
    onRecipientClick: (UserSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.tab_notifications),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onMarkAllRead, enabled = state.hasUnread) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text(stringResource(R.string.notifications_mark_all_read))
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(NotificationCategory.entries, key = { it.name }) { category ->
                    FilterChip(
                        selected = category == state.selectedCategory,
                        onClick = { onCategoryChange(category) },
                        label = { Text(category.label()) },
                        colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        trailingIcon = {
                            // A plain tabular numeral, not a Badge: the count belongs to the chip's own
                            // colour pair, and Badge would drop a red pill into a row of brand chips.
                            val count = state.counts.forCategory(category)
                            if (count > 0) {
                                Text(
                                    text = count.coerceAtMost(MAX_BADGE).toString(),
                                    style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = TABULAR_FIGURES,
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    !state.isSignedIn -> SignedOutState(onSignIn = onSignIn)

                    state.isLoading && state.isEmpty -> LoadingState()

                    state.error != null && state.isEmpty ->
                        NodeSeekErrorState(
                            error = state.error,
                            onRetry = onRetry,
                            onOpenBrowser = onVerify,
                            onSignIn = onSignIn,
                        )

                    state.selectedCategory == NotificationCategory.MESSAGES ->
                        ConversationList(
                            state = state,
                            onConversationClick = onConversationClick,
                            onNewConversation = onNewConversation,
                            onQueryChange = onNewConversationQueryChange,
                            onSearch = onNewConversationSearch,
                            onDismiss = onNewConversationDismiss,
                            onRecipientClick = onRecipientClick,
                        )

                    state.items.isEmpty() ->
                        EmptyNotifications(
                            modifier = Modifier.align(Alignment.Center),
                            onRefresh = onRetry,
                        )

                    else ->
                        LazyColumn {
                            items(state.items, key = ForumNotification::id) { item ->
                                NotificationRow(
                                    item = item,
                                    nowMillis = state.nowMillis,
                                    onClick = { onNotificationClick(item) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: ForumNotification,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(
                if (item.isUnread) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ).clickable(enabled = item.postId != null, onClick = onClick)
            .padding(start = 10.dp, end = Spacing.lg, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Aligned with the first line of the sentence rather than the top of the row.
        Box(Modifier.padding(top = 7.dp).size(6.dp)) {
            if (item.isUnread) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        UserAvatar(url = item.avatarUrl, name = item.actorName, size = Sizes.avatarList)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = notificationSentence(item),
                style = MaterialTheme.typography.bodyMedium,
                color =
                if (item.isUnread) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            timestampLabel(item.createdAtMillis, item.createdAtText, nowMillis)?.let { stamp ->
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "{actor} 在帖子 {title} 中@了我", with the actor and the thread carrying their own emphasis.
 *
 * Built from the template's placeholders instead of `String.format` + `indexOf`, so a user whose
 * name also occurs inside the thread title cannot shift the spans onto the wrong words.
 */
@Composable
private fun notificationSentence(item: ForumNotification): AnnotatedString {
    val template =
        stringResource(
            when (item.category) {
                NotificationCategory.REPLIES -> R.string.notification_sentence_reply
                else -> R.string.notification_sentence_mention
            },
        )
    val title = item.threadTitle ?: stringResource(R.string.notification_unknown_thread)
    val actorStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val titleStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    return buildAnnotatedString {
        var cursor = 0
        PLACEHOLDER.findAll(template).forEach { match ->
            append(template.substring(cursor, match.range.first))
            when (match.groupValues[1]) {
                "1" -> withStyle(actorStyle) { append(item.actorName) }
                else -> withStyle(titleStyle) { append(title) }
            }
            cursor = match.range.last + 1
        }
        append(template.substring(cursor))
    }
}

/** `26 分钟前 · 2026/7/26 09:56:03`, or the server's own wording when it sent no parsable time. */
@Composable
internal fun timestampLabel(
    millis: Long?,
    fallback: String?,
    nowMillis: Long,
): String? =
    when {
        millis == null -> fallback

        else ->
            stringResource(
                R.string.notification_time_pair,
                TimeFormat.relative(millis, nowMillis),
                TimeFormat.absolute(millis),
            )
    }

@Composable
private fun EmptyNotifications(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            Icons.Default.MailOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(R.string.notifications_empty), style = MaterialTheme.typography.titleSmall)
        Button(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun NotificationCategory.label(): String =
    stringResource(
        when (this) {
            NotificationCategory.MENTIONS -> R.string.notifications_mentions
            NotificationCategory.REPLIES -> R.string.notifications_replies
            NotificationCategory.MESSAGES -> R.string.notifications_messages
        },
    )

/** `%1$s` / `%2$s` in the sentence templates; the class keeps the dollar out of the raw string. */
private val PLACEHOLDER = Regex("""%(\d)[$]s""")
private const val MAX_BADGE = 99

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun NotificationsPreview() {
    NodysseyTheme {
        NotificationsScreen(
            state =
            NotificationsUiState(
                isSignedIn = true,
                counts = NotificationCounts(replies = 5, mentions = 2, messages = 3),
                nowMillis = PREVIEW_NOW,
                items =
                listOf(
                    ForumNotification(
                        id = "1",
                        category = NotificationCategory.MENTIONS,
                        postId = 1,
                        floor = "#3",
                        actorUid = 12,
                        actorName = "nssk",
                        avatarUrl = null,
                        excerpt = null,
                        threadTitle = "求教如何改用户名",
                        createdAtMillis = PREVIEW_NOW - 26 * 60_000L,
                        createdAtText = null,
                        isUnread = true,
                    ),
                    ForumNotification(
                        id = "2",
                        category = NotificationCategory.MENTIONS,
                        postId = 2,
                        floor = null,
                        actorUid = 13,
                        actorName = "羽落无声",
                        avatarUrl = null,
                        excerpt = null,
                        threadTitle = "Debian 13 上用 nftables 做端口转发的坑",
                        createdAtMillis = PREVIEW_NOW - 26 * 60 * 60_000L,
                        createdAtText = null,
                        isUnread = false,
                    ),
                ),
            ),
            onSignIn = {},
            onVerify = {},
            onCategoryChange = {},
            onRetry = {},
            onMarkAllRead = {},
            onNotificationClick = {},
            onConversationClick = {},
            onNewConversation = {},
            onNewConversationQueryChange = {},
            onNewConversationSearch = {},
            onNewConversationDismiss = {},
            onRecipientClick = {},
        )
    }
}

/** Fixed so the preview's relative labels do not drift with the render clock. */
internal const val PREVIEW_NOW = 1_785_000_000_000L
