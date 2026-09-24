package io.github.nodyssey.ui.notifications

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationSource
import io.github.nodyssey.data.NotificationTab
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.ui.common.SignedOutState
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.webViewUrl
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.notification_sentence_mention
import io.github.nodyssey.ui.resources.notification_sentence_reply
import io.github.nodyssey.ui.resources.notification_sentence_reply_mention
import io.github.nodyssey.ui.resources.notification_time_pair
import io.github.nodyssey.ui.resources.notification_unknown_thread
import io.github.nodyssey.ui.resources.notifications_empty
import io.github.nodyssey.ui.resources.notifications_interactions
import io.github.nodyssey.ui.resources.notifications_mark_all_read
import io.github.nodyssey.ui.resources.notifications_messages
import io.github.nodyssey.ui.resources.notifications_section_earlier
import io.github.nodyssey.ui.resources.notifications_section_today
import io.github.nodyssey.ui.resources.tab_notifications
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.TabLabel
import io.github.plaza.designsys.component.UnderlineTabRow
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.cardShadow
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
fun NotificationsRoute(
    viewModel: NotificationsViewModel,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
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
        onTabChange = viewModel::selectTab,
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
 * Boards 7d and 7e, redrawn as 5a and 5b: a header that lifts off the page as one piece once a list
 * scrolls under it, and the rows in white cards on the grey page.
 *
 * One screen rather than two: 私信 is a *group* of the same 通知 tab on the site, so it keeps the
 * title, the 全部已读 action and the group tabs and swaps only the list underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onTabChange: (NotificationTab) -> Unit,
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
        if (state.selectedTab == NotificationTab.MESSAGES) {
            conversationListState.animateScrollToItem(0)
        } else {
            notificationListState.animateScrollToItem(0)
        }
    }
    /*
     * 左右滑动切换分组.
     *
     * The two groups are pages of one pager, so 私信 arrives with the finger rather than after it —
     * both lists are loaded together for exactly this reason; see [NotificationsViewModel.refresh].
     *
     * The pager also settles the gesture: the list inside claims a drag that crosses its own slop
     * vertically first, and from then on the pager sees every change as consumed and cannot start.
     */
    val tabs = NotificationTab.entries
    val selectedIndex = tabs.indexOf(state.selectedTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { tabs.size }

    // A settled page is a decision; anything before it is a gesture still being made, and switching
    // group mid-swipe would refresh both lists under a finger that had not chosen yet.
    val currentTab by rememberUpdatedState(state.selectedTab)
    val currentOnTabChange by rememberUpdatedState(onTabChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            // Coming to rest on the selected group says nothing — that is every first composition,
            // and every page this effect was the one to move.
            tabs.getOrNull(page)?.takeIf { it != currentTab }?.let { currentOnTabChange(it) }
        }
    }

    // The other direction: a tab tapped, or a group restored with the screen.
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }

    // The header lifts as one piece — title, 全部已读 and the tabs — once a list runs under it (5a).
    // [OneHandTopAppBar] lifts itself; the tab row below it has to follow in step, or the shadow
    // would fall between the two halves of one header instead of under it.
    val layers = LocalPlazaLayers.current
    // The lift follows the list on the page in view, not only the scrolls the bar heard: switching
    // to the other group, or 回顶 from the tab bar, moves no nested scroll, and the header stayed
    // lifted — its shadow and the fade under it — over a list back at its top.
    val currentList = if (tabs.getOrNull(pagerState.currentPage) == NotificationTab.MESSAGES) conversationListState else notificationListState
    LaunchedEffect(currentList) {
        snapshotFlow { currentList.canScrollBackward }.collect { appBarState.syncContentOverlapped(it) }
    }
    val lifted = appBarState.isContentOverlapped
    val headerColor by animateColorAsState(
        targetValue =
        if (lifted && !layers.shadows) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            layers.page
        },
        // A snap under reduced motion and 墨水屏, like the bar's own tint this has to keep pace with.
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "notificationsHeader",
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            // `readableWidth` stays on the header itself: the content column below is constrained
            // the same way, and a full-bleed bar over a centred list is the one thing this screen
            // has never done.
            Column(Modifier.readableWidth()) {
                OneHandTopAppBar(
                    title = stringResource(Res.string.tab_notifications),
                    state = appBarState,
                    actions = {
                        TextButton(onClick = onMarkAllRead, enabled = state.hasUnread) {
                            Icon(
                                PlazaIcons.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(Res.string.notifications_mark_all_read))
                        }
                    },
                )
                /*
                 * Tabs rather than the filter chips this row used to be.
                 *
                 * The groups are pages now, and a tab row is the control that says so: the indicator
                 * is attached to the page underneath and moves with it, which a row of pills has no
                 * way to draw. Fixed rather than scrollable — there are two of them, and a scrollable
                 * row would huddle both at the start with the rest of the width left over.
                 *
                 * Drawn over an opaque strip of the header's own colour, which is also what covers the
                 * bar's shadow where the two halves meet.
                 */
                Surface(
                    color = headerColor,
                    modifier = Modifier.cardShadow(RectangleShape, enabled = lifted && layers.shadows),
                ) {
                    GroupTabs(
                        tabs = tabs,
                        currentPage = pagerState.currentPage,
                        counts = state.counts,
                        onTabChange = onTabChange,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
            when {
                // Signing in is not one of the groups: with no session there is nothing to swipe
                // between, and a pager over two empty lists would say so twice.
                !state.isSignedIn -> SignedOutState(onSignIn = onSignIn)

                else ->
                    HorizontalPager(
                        state = pagerState,
                        key = { index -> tabs[index].name },
                        modifier = Modifier.fillMaxSize(),
                    ) { index ->
                        val tab = tabs[index]
                        // `isEmpty` keeps the first load out of the indicator: that one already draws
                        // [LoadingState] in the middle of the screen, and a spinner above a spinner
                        // reads as two different loads.
                        PullToRefreshBox(
                            isRefreshing = state.isLoading && !state.isEmptyOf(tab),
                            onRefresh = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Inside the refresh box rather than on the `Scaffold`, which is the
                            // whole of the arbitration: post-scroll runs innermost first, so the
                            // deeper of the two gets the leftover downward drag. In here the bar
                            // sinks first and — once full, consuming nothing — hands the rest of the
                            // pull on to the refresh. Out on the `Scaffold` the refresh would take
                            // it all and the big title could never be pulled back.
                            //
                            // The reader gets one gesture with two stages in it: pull to bring the
                            // screen down, keep pulling to refresh. Releasing mid-sink leaves the
                            // bar where it is, so the next pull starts from there and reaches the
                            // refresh sooner — and from the top of the page, where the bar is
                            // already full, the very first pull is the refresh.
                            //
                            // The spinner comes down from here rather than from the top of the
                            // window, which is the right place for it and nobody had to arrange it:
                            // the refresh wraps the content, so it starts wherever the bar has left
                            // the content standing.
                            Box(Modifier.fillMaxSize().nestedScroll(appBarState.nestedScrollConnection)) {
                                NotificationGroup(
                                    tab = tab,
                                    state = state,
                                    notificationListState = notificationListState,
                                    conversationListState = conversationListState,
                                    onSignIn = onSignIn,
                                    onVerify = onVerify,
                                    onRetry = onRetry,
                                    onNotificationClick = onNotificationClick,
                                    onConversationClick = onConversationClick,
                                    onNewConversation = onNewConversation,
                                    onNewConversationQueryChange = onNewConversationQueryChange,
                                    onNewConversationSearch = onNewConversationSearch,
                                    onNewConversationDismiss = onNewConversationDismiss,
                                    onRecipientClick = onRecipientClick,
                                )
                            }
                        }
                    }
            }
            // 渐隐: once the list runs under the header, its rows fade into the page rather than
            // being cut off at the header's edge (5a). Only then — at rest there is nothing under
            // the header, and the fade would only dim the first group's label.
            if (lifted) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TOP_FADE)
                        // From the header's own colour, which is a step lighter than the page once
                        // lifted in dark: starting from the page drew a dark seam under the header.
                        .background(Brush.verticalGradient(listOf(headerColor, headerColor.copy(alpha = 0f)))),
                )
            }
        }
    }
}

/**
 * The two groups' tabs, each with its unread count (5a).
 *
 * The count is a Material [Badge] in its own error red — the artboard's choice, and the same mark the
 * tab bar's 通知 icon carries, so a count reads alike wherever it appears on the screen.
 */
@Composable
private fun GroupTabs(
    tabs: List<NotificationTab>,
    currentPage: Int,
    counts: NotificationCounts,
    onTabChange: (NotificationTab) -> Unit,
) {
    // The pager's own page rather than the selected group: the indicator starts moving as the swipe
    // passes the halfway point instead of waiting for the gesture to end, which is what makes it read
    // as the page's own label. The group itself still changes when the gesture comes to rest.
    UnderlineTabRow(
        selectedTabIndex = currentPage,
        tabs =
        tabs.map { tab ->
            TabLabel(tab.label(), counts.forTab(tab).takeIf { it > 0 }?.let { unreadLabel(it, MAX_BADGE) })
        },
        onSelect = { onTabChange(tabs[it]) },
    )
}

/**
 * One group, as one page of the pager: its rows, or whatever it has instead of them.
 *
 * Loading and failure are asked about per group rather than per screen — the two are different
 * endpoints, and the page beside the selected one is half on screen for the whole of a swipe, so a
 * 私信 failure must not draw an error over 通知's rows.
 */
@Composable
private fun BoxScope.NotificationGroup(
    tab: NotificationTab,
    state: NotificationsUiState,
    notificationListState: LazyListState,
    conversationListState: LazyListState,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onRetry: () -> Unit,
    onNotificationClick: (ForumNotification) -> Unit,
    onConversationClick: (MessageConversation) -> Unit,
    onNewConversation: () -> Unit,
    onNewConversationQueryChange: (String) -> Unit,
    onNewConversationSearch: () -> Unit,
    onNewConversationDismiss: () -> Unit,
    onRecipientClick: (UserSearchResult) -> Unit,
) {
    val isEmpty = state.isEmptyOf(tab)
    val error = state.errors[tab]
    when {
        state.isLoading && isEmpty -> LoadingState()

        error != null && isEmpty ->
            SiteErrorState(
                error = error,
                onRetry = onRetry,
                onOpenBrowser = { onVerify(error.webViewUrl(NodeSeekSite.BASE_URL)) },
                onVerify = onVerify,
                onSignIn = onSignIn,
            )

        // 私信 draws its own empty state, under the 新建私信 button that is the answer to it.
        tab == NotificationTab.MESSAGES ->
            ConversationList(
                state = state,
                listState = conversationListState,
                onConversationClick = onConversationClick,
                onNewConversation = onNewConversation,
                onQueryChange = onNewConversationQueryChange,
                onSearch = onNewConversationSearch,
                onDismiss = onNewConversationDismiss,
                onRecipientClick = onRecipientClick,
                onSignIn = onSignIn,
                onVerify = onVerify,
            )

        isEmpty ->
            EmptyNotifications(
                modifier = Modifier.align(Alignment.Center),
                onRefresh = onRetry,
            )

        else -> {
            val rows = remember(state.items, state.nowMillis) { notificationRows(state.items, state.nowMillis) }
            // fillMaxSize for the same reason 私信's list has it: a list shorter than the screen
            // leaves the rest of the page dispatching nothing, and the gesture that reopens the
            // one-hand title is a pull on the page.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = notificationListState,
                contentPadding =
                PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = 2.dp, bottom = Spacing.lg),
            ) {
                items(rows, key = NotificationListRow::key) { row ->
                    when (row) {
                        is NotificationListRow.Day -> SectionLabel(
                            stringResource(row.bucket.label()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentPadding = ListGroupLabelPadding,
                        )

                        is NotificationListRow.Item ->
                            NotificationRow(
                                item = row.item,
                                first = row.first,
                                last = row.last,
                                nowMillis = state.nowMillis,
                                onClick = { onNotificationClick(row.item) },
                            )
                    }
                }
            }
        }
    }
}

/** 今天 or 更早 — the two headings 5a splits the interactions into. */
internal enum class NotificationDay { TODAY, EARLIER }

private fun NotificationDay.label(): StringResource =
    when (this) {
        NotificationDay.TODAY -> Res.string.notifications_section_today
        NotificationDay.EARLIER -> Res.string.notifications_section_earlier
    }

internal sealed interface NotificationListRow {
    val key: String

    data class Day(val bucket: NotificationDay) : NotificationListRow {
        override val key get() = "day-${bucket.name}"
    }

    /** [first] and [last] say which slice of its day's card the row draws; see [CardSliceRow]. */
    data class Item(
        val item: ForumNotification,
        val first: Boolean,
        val last: Boolean,
    ) : NotificationListRow {
        override val key get() = item.id
    }
}

/**
 * The interactions under their day headings, one card per day.
 *
 * Calendar days, as 阅读历史 counts them: a reply at 00:30 is 今天's even though it is "22 小时前" by
 * the clock. Two buckets rather than history's four because 5a asks for two, and because the row's own
 * time line already says 昨天 or 3 天前 — a heading for each would only repeat it. The server's order is
 * kept inside a bucket; a row with no parsable time has no day to be put in, so it goes to 更早 rather
 * than claiming to be new.
 */
internal fun notificationRows(
    items: List<ForumNotification>,
    nowMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<NotificationListRow> {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    return items
        .groupBy { item ->
            val day = item.createdAtMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date }
            if (day != null && day.daysUntil(today) <= 0) NotificationDay.TODAY else NotificationDay.EARLIER
        }.entries
        .sortedBy { it.key.ordinal }
        .flatMap { (bucket, rows) ->
            listOf(NotificationListRow.Day(bucket)) +
                rows.mapIndexed { index, item ->
                    NotificationListRow.Item(item, first = index == 0, last = index == rows.lastIndex)
                }
        }
}

/**
 * One interaction: who, what, where, when — and a dot while it is unread (5a).
 *
 * Unread no longer tints the row: on a white card a tinted band reads as a selection. The dot at the
 * end, the full-strength sentence and the thread title in primary carry it instead, and a read row
 * falls back to grey throughout.
 */
@Composable
private fun NotificationRow(
    item: ForumNotification,
    first: Boolean,
    last: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    GroupedListItem(
        first = first,
        last = last,
        enabled = item.postId != null,
        onClick = onClick,
        verticalAlignment = Alignment.Top,
        leadingContent = {
            UserAvatar(url = item.avatarUrl, name = item.actorName, size = NOTIFICATION_AVATAR)
        },
        headlineContent = {
            Text(
                text = notificationSentence(item),
                color =
                if (item.isUnread) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent =
        timestampLabel(item.createdAtMillis, item.createdAtText, nowMillis)?.let { stamp -> { Text(stamp) } },
        // The slot is kept on read rows too, so a sentence wraps at the same width whichever state it
        // is in and marking the list read does not reflow it. Dropped to sit on the sentence's first
        // line rather than above it.
        trailingContent = {
            Box(Modifier.padding(top = 7.dp).size(UNREAD_DOT)) {
                if (item.isUnread) {
                    Box(
                        Modifier
                            .size(UNREAD_DOT)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        },
    )
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
            when {
                // A reply that opens with `@name #7` is both, and saying only one of them would be
                // the merge showing through as a half-truth.
                item.isReply && item.isMention -> Res.string.notification_sentence_reply_mention

                item.isReply -> Res.string.notification_sentence_reply

                else -> Res.string.notification_sentence_mention
            },
        )
    val title = item.threadTitle ?: stringResource(Res.string.notification_unknown_thread)
    // A read row keeps the weights and drops the colours: the thread title goes back to the grey of
    // the rest of the sentence, which is what lets the unread rows above it stand out (5a).
    val actorStyle = SpanStyle(fontWeight = FontWeight.SemiBold)
    val titleStyle =
        if (item.isUnread) {
            SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        } else {
            SpanStyle()
        }
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
                Res.string.notification_time_pair,
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
        Text(stringResource(Res.string.notifications_empty), style = MaterialTheme.typography.titleSmall)
        Button(onClick = onRefresh) { Text(stringResource(Res.string.action_retry)) }
    }
}

@Composable
private fun NotificationTab.label(): String =
    stringResource(
        when (this) {
            NotificationTab.INTERACTIONS -> Res.string.notifications_interactions
            NotificationTab.MESSAGES -> Res.string.notifications_messages
        },
    )

/** `%1$s` / `%2$s` in the sentence templates; the class keeps the dollar out of the raw string. */
private val PLACEHOLDER = Regex("""%(\d)[$]s""")
private const val MAX_BADGE = 99

/** 5a's measurements: a 40dp avatar 14dp in from the card's edge, 12dp from the sentence. */
private val NOTIFICATION_AVATAR = 40.dp
internal val ROW_PADDING_H = 14.dp
internal val ROW_GAP = 12.dp
private val UNREAD_DOT = 8.dp

/** How far the page colour reaches down over a list that has scrolled under the header. */
private val TOP_FADE = 20.dp

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
                    // The folded row: one comment that both replied and @-ed, as most of them are.
                    ForumNotification(
                        id = "1",
                        sources =
                        listOf(
                            NotificationSource(NotificationCategory.REPLIES, 1L, isUnread = true),
                            NotificationSource(NotificationCategory.MENTIONS, 9L, isUnread = true),
                        ),
                        commentId = 1L,
                        postId = 1,
                        floor = "#3",
                        actorUid = 12,
                        actorName = "nssk",
                        avatarUrl = null,
                        threadTitle = "求教如何改用户名",
                        createdAtMillis = PREVIEW_NOW - 26 * 60_000L,
                        createdAtText = null,
                    ),
                    ForumNotification(
                        id = "2",
                        sources =
                        listOf(
                            NotificationSource(NotificationCategory.MENTIONS, 2L, isUnread = false),
                        ),
                        commentId = 2L,
                        postId = 2,
                        floor = null,
                        actorUid = 13,
                        actorName = "羽落无声",
                        avatarUrl = null,
                        threadTitle = "Debian 13 上用 nftables 做端口转发的坑",
                        createdAtMillis = PREVIEW_NOW - 26 * 60 * 60_000L,
                        createdAtText = null,
                    ),
                ),
            ),
            onSignIn = {},
            onVerify = {},
            onTabChange = {},
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
