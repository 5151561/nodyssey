package io.github.nsreader.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.data.ForumNotification
import io.github.nsreader.data.NotificationCategory
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.SignedOutState
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun NotificationsRoute(
    viewModel: NotificationsViewModel,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onNotificationClick: (ForumNotification) -> Unit,
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
        modifier = modifier,
    )
}

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
                TextButton(
                    onClick = onMarkAllRead,
                    enabled = state.items.any(ForumNotification::isUnread),
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null)
                    Text(stringResource(R.string.notifications_mark_all_read))
                }
            }

            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(NotificationCategory.entries, key = { it.name }) { category ->
                    FilterChip(
                        selected = category == state.selectedCategory,
                        onClick = { onCategoryChange(category) },
                        label = { Text(category.label()) },
                        trailingIcon = {
                            val count = state.counts.forCategory(category)
                            if (count > 0) Badge { Text(count.coerceAtMost(99).toString()) }
                        },
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    !state.isSignedIn -> SignedOutState(onSignIn = onSignIn)

                    state.isLoading && state.items.isEmpty() -> LoadingState()

                    state.error != null && state.items.isEmpty() ->
                        NodeSeekErrorState(
                            error = state.error,
                            onRetry = onRetry,
                            onOpenBrowser =
                            if (state.error == io.github.nsreader.core.net.NodeSeekError.LoginRequired) {
                                onSignIn
                            } else {
                                onVerify
                            },
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
                                    onClick = { onNotificationClick(item) },
                                )
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).padding(top = 2.dp)) {
            if (item.isUnread) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        UserAvatar(url = null, name = item.actorName, size = 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = item.actorName + " " + item.action,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            )
            item.excerpt?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val footer = listOfNotNull(item.threadTitle, item.createdAt).joinToString(" · ")
            if (footer.isNotEmpty()) {
                Text(
                    text = footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
        androidx.compose.material3.Icon(
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
            NotificationCategory.REPLIES -> R.string.notifications_replies
            NotificationCategory.MENTIONS -> R.string.notifications_mentions
            NotificationCategory.MESSAGES -> R.string.notifications_messages
            NotificationCategory.SYSTEM -> R.string.notifications_system
        },
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun NotificationsPreview() {
    NodeSeekTheme {
        NotificationsScreen(
            state =
            NotificationsUiState(
                isSignedIn = true,
                items =
                listOf(
                    ForumNotification(
                        id = "1",
                        postId = 1,
                        floor = "#3",
                        actorName = "zhh123",
                        action = "回复了你的帖子",
                        excerpt = "你试试前台等，一般是浏览器把下载放后台了",
                        threadTitle = "为啥 nodequality 复制格式非常慢…",
                        createdAt = "5分钟前",
                        isUnread = true,
                    ),
                ),
            ),
            onSignIn = {},
            onVerify = {},
            onCategoryChange = {},
            onRetry = {},
            onMarkAllRead = {},
            onNotificationClick = {},
        )
    }
}
