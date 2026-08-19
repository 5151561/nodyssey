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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.ui.common.SignedOutState
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.AvatarCapOffset
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.listAvatarSize
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch

@Composable
fun NotificationsRoute(
    viewModel: NotificationsViewModel,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onNotificationClick: (ForumNotification) -> Unit,
    onOpenThread: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequests: Int = 0,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Coming back into view is the refresh trigger this screen was missing: the view model outlives
    // every tab switch, so nothing recreates it, and ON_RESUME is the one signal that covers all the
    // ways back — the tab bar, Back from a thread, the app returning to the foreground.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfStale()
        onPauseOrDispose {}
    }
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
        scrollToTopRequests = scrollToTopRequests,
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
    scrollToTopRequests: Int = 0,
) {
    val appBarState = rememberOneHandAppBarState()
    // One list state per group rather than one shared: 通知 and 私信 are different lists of different
    // things, and returning to a group ought to return to where it was left.
    val notificationListState = rememberLazyListState()
    val conversationListState = rememberLazyListState()
    /*
     * Which 通知 tap has already been answered. Remembered across leaving the composition, because
     * opening a notification takes this screen out of it and a plain `LaunchedEffect` on the count
     * would fire again on the way back — throwing away the position the list had just restored. A
     * tap from before the thread was opened is not a request to scroll after returning from it.
     */
    var answeredScrollRequest by rememberSaveable { mutableIntStateOf(scrollToTopRequests) }
    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests == answeredScrollRequest) return@LaunchedEffect
        answeredScrollRequest = scrollToTopRequests
        // Alongside the scroll rather than before it: the title coming down and the list running up
        // are one movement, and awaiting the bar first would play them as two.
        launch { appBarState.unfold() }
        if (state.selectedCategory == NotificationCategory.MESSAGES) {
            conversationListState.animateScrollToItem(0)
        } else {
            notificationListState.animateScrollToItem(0)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            // `readableWidth` stays on the bar itself: the content column below is constrained the
            // same way, and a full-bleed bar over a centred list is the one thing this screen has
            // never done.
            OneHandTopAppBar(
                modifier = Modifier.readableWidth(),
                title = stringResource(R.string.tab_notifications),
                state = appBarState,
                actions = {
                    TextButton(onClick = onMarkAllRead, enabled = state.hasUnread) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text(stringResource(R.string.notifications_mark_all_read))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
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
                                    text = unreadLabel(count, MAX_BADGE),
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

            // `isEmpty` keeps the first load out of the indicator: that one already draws
            // [LoadingState] in the middle of the screen, and a spinner above a spinner reads as two
            // different loads.
            PullToRefreshBox(
                isRefreshing = state.isLoading && !state.isEmpty,
                onRefresh = onRetry,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Inside the refresh box rather than on the `Scaffold`, which is the whole of the
                // arbitration: post-scroll runs innermost first, so the deeper of the two gets the
                // leftover downward drag. In here the bar sinks first and — once full, consuming
                // nothing — hands the rest of the pull on to the refresh. Out on the `Scaffold` the
                // refresh would take it all and the big title could never be pulled back.
                //
                // The reader gets one gesture with two stages in it: pull to bring the screen down,
                // keep pulling to refresh. Releasing mid-sink leaves the bar where it is, so the
                // next pull starts from there and reaches the refresh sooner — and from the top of
                // the page, where the bar is already full, the very first pull is the refresh.
                //
                // The spinner comes down from here rather than from the top of the window, which is
                // the right place for it and nobody had to arrange it: the refresh wraps the
                // content, so it starts wherever the bar has left the content standing.
                Box(Modifier.fillMaxSize().nestedScroll(appBarState.nestedScrollConnection)) {
                    when {
                        !state.isSignedIn -> SignedOutState(onSignIn = onSignIn)

                        state.isLoading && state.isEmpty -> LoadingState()

                        state.error != null && state.isEmpty ->
                            SiteErrorState(
                                error = state.error,
                                onRetry = onRetry,
                                onOpenBrowser = onVerify,
                                onSignIn = onSignIn,
                            )

                        state.selectedCategory == NotificationCategory.MESSAGES ->
                            ConversationList(
                                state = state,
                                listState = conversationListState,
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
                            LazyColumn(state = notificationListState) {
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
        // Both markers hang off the sentence's first line, not off the top of the row — see
        // [AvatarCapOffset]. The dot is smaller than the avatar, so it drops further to centre on
        // the same line rather than sitting on top of it.
        Box(Modifier.offset(y = AvatarCapOffset + 2.dp).size(6.dp)) {
            if (item.isUnread) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        UserAvatar(
            url = item.avatarUrl,
            name = item.actorName,
            size = listAvatarSize(),
            modifier = Modifier.offset(y = AvatarCapOffset),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = notificationSentence(item),
                // Trimmed at the top so the sentence starts at the row's edge instead of a few
                // pixels below it, the same pairing the feed's title and avatar use.
                style =
                MaterialTheme.typography.bodyMedium.copy(
                    lineHeightStyle =
                    LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Proportional,
                        trim = LineHeightStyle.Trim.FirstLineTop,
                    ),
                ),
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

/**
 * Drops the avatar onto the sentence's cap line.
 *
 * The twin of the feed's own offset — even with the first line's leading trimmed, the line box still
 * starts at the ascent rather than at the tallest glyph, and a top-aligned avatar next to it reads
 * as floating. Measured at bodyMedium on the row as built, which is why it is not the feed's number.
 */
private val AvatarCapOffset = 3.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun NotificationsPreview() {
    PlazaTheme {
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
                        viewedId = 1L,
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
                        viewedId = 2L,
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
