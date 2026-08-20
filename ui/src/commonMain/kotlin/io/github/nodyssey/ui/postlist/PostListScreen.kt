package io.github.nodyssey.ui.postlist

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.EmptyFeedState
import io.github.nodyssey.ui.common.JumpDestination
import io.github.nodyssey.ui.common.LocalThreadTransition
import io.github.nodyssey.ui.common.NavigationBarScrollConnection
import io.github.nodyssey.ui.common.NavigationDirectionThreshold
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.PageJumpRail
import io.github.nodyssey.ui.common.PageJumpSheet
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.appName
import io.github.nodyssey.ui.common.sharedThreadAuthor
import io.github.nodyssey.ui.common.sharedThreadAvatar
import io.github.nodyssey.ui.common.sharedThreadBoard
import io.github.nodyssey.ui.common.sharedThreadTitle
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_create_post
import io.github.nodyssey.ui.resources.action_scroll_to_top
import io.github.nodyssey.ui.resources.action_sort
import io.github.nodyssey.ui.resources.feed_page_size_note
import io.github.nodyssey.ui.resources.page_jump_newest
import io.github.nodyssey.ui.resources.post_badge_awarded
import io.github.nodyssey.ui.resources.post_badge_locked
import io.github.nodyssey.ui.resources.post_badge_locked_level
import io.github.nodyssey.ui.resources.post_badge_pinned
import io.github.nodyssey.ui.resources.post_new_reply_count
import io.github.nodyssey.ui.resources.post_reply_count
import io.github.nodyssey.ui.resources.post_view_count
import io.github.nodyssey.ui.resources.sort_by_post_time
import io.github.nodyssey.ui.resources.sort_by_reply_time
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.AvatarCapOffset
import io.github.plaza.designsys.component.AvatarShape
import io.github.plaza.designsys.component.MetaStat
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.ThreadRow
import io.github.plaza.designsys.component.ThreadRowTitle
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.listAvatarSize
import io.github.plaza.designsys.component.textScaledSize
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful entry point. It only wires the ViewModel to the stateless [PostListScreen] below, which is
 * what keeps the screen previewable and testable without a running app.
 */
@Composable
fun PostListRoute(
    viewModel: PostListViewModel,
    listState: LazyListState,
    onPostClick: (FeedPost) -> Unit,
    onCreatePost: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
    scrollToTopRequests: Int = 0,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PostListScreen(
        state = state,
        posts = viewModel.feed.collectAsLazyPagingItems(),
        listState = listState,
        onPostClick = onPostClick,
        onCreatePost = onCreatePost,
        onBoardClick = viewModel::selectCategory,
        onArrangementChange = viewModel::saveBoardArrangement,
        onSortChange = viewModel::selectSort,
        onGoToPage = viewModel::goToPage,
        onFindPageRow = viewModel::rowIndexOfPage,
        onSignInClick = onSignIn,
        // The challenge is cleared on the URL that failed, so the WebView loads the same list page the
        // request did — a different page can be served without a challenge and prove nothing.
        onRecoverInBrowser = { onVerify(viewModel.challengeUrl()) },
        modifier = modifier,
        onNavigationBarHiddenChanged = onNavigationBarHiddenChanged,
        scrollToTopRequests = scrollToTopRequests,
    )
}

/**
 * Stateless list.
 *
 * Rows arrive as [LazyPagingItems], so appending, retrying and the end-of-list condition are Paging's
 * concern rather than hand-written bookkeeping. Previews stay possible because a [PagingData] can be
 * built from a plain list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(
    state: PostListUiState,
    posts: LazyPagingItems<FeedPost>,
    onPostClick: (FeedPost) -> Unit,
    onBoardClick: (String?) -> Unit,
    onSortChange: (FeedSort) -> Unit,
    onSignInClick: () -> Unit,
    onRecoverInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Reloads the feed from a page it is not currently holding. Only 首页翻页栏 calls it, and only
     * when the target is genuinely absent — a page already in the window is scrolled to instead.
     */
    onGoToPage: (Int) -> Unit = {},
    /**
     * Where a page starts among the rows already stored, or null when none of it is. Answered by the
     * database rather than by [posts], which holds one window and calls everything outside it absent.
     */
    onFindPageRow: suspend (Int) -> Int? = { null },
    listState: LazyListState = rememberLazyListState(),
    /** Commits an edit made on the board strip itself: the pill order, and which boards are parked. */
    onArrangementChange: (order: List<String>, parked: Set<String>) -> Unit = { _, _ -> },
    onCreatePost: () -> Unit = {},
    /** Keeps the host navigation bar hidden until the user deliberately scrolls toward the list start. */
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
    /**
     * How many times the host has asked for the top of the feed — one per tap on the already-selected
     * 首页 tab. Any increase scrolls; the count itself means nothing, which is what lets two taps in a
     * row read as two requests without the host having to clear a flag afterwards.
     */
    scrollToTopRequests: Int = 0,
) {
    val directionThresholdPx = with(LocalDensity.current) { NavigationDirectionThreshold.toPx() }
    val currentOnNavigationBarHiddenChanged by
        rememberUpdatedState(onNavigationBarHiddenChanged)
    var navigationBarHidden by remember { mutableStateOf(false) }
    val navigationBarScrollConnection =
        remember(directionThresholdPx) {
            NavigationBarScrollConnection(directionThresholdPx) { hidden ->
                navigationBarHidden = hidden
                currentOnNavigationBarHiddenChanged(hidden)
            }
        }

    /*
     * The wordmark row rides the scroll; the board strip under it does not.
     *
     * Only the 大标题栏 is given a scroll behaviour, so the strip — which is navigation, and the one
     * thing a reader reaches for mid-feed — stays put while the title and 排序 fold away. `enterAlways`
     * rather than the navigation bar's sticky threshold: the bar is what the sort action lives in, and
     * a short drag back up has to be enough to reach it.
     */
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    /**
     * Unfolds the title without a gesture, for the jumps that put the list back at its first row.
     *
     * The bar collapses only against scroll deltas it has consumed, so a programmatic scroll leaves it
     * folded at the top of the feed — the one place there is nothing left to scroll back up through.
     */
    fun revealTopBar() {
        topBarScrollBehavior.state.heightOffset = 0f
        topBarScrollBehavior.state.contentOffset = 0f
    }

    /*
     * Switching boards or sort order is the one case where the previous scroll offset is meaningless.
     *
     * Coming back from a thread is not a switch, but it looks like one from inside a `LaunchedEffect`:
     * the screen left the composition while the thread was open, so the effect starts again on the
     * same board and used to throw away the offset the list had just restored. Remembering which feed
     * the list was last reset for — saved alongside that offset, so it survives the same trips — is
     * what tells a real switch apart from a return.
     */
    var lastResetFeed by rememberSaveable { mutableStateOf(feedIdentity(state)) }
    LaunchedEffect(state.categorySlug, state.sort, state.startPage) {
        val feed = feedIdentity(state)
        if (feed != lastResetFeed) {
            lastResetFeed = feed
            revealTopBar()
            listState.scrollToItem(0)
        }
    }

    // The host must not stay stuck bar-less if this screen leaves the composition.
    DisposableEffect(Unit) { onDispose { currentOnNavigationBarHiddenChanged(false) } }

    /*
     * The two ways back to the start of the feed: the 首页 tab, and the wordmark above the list.
     *
     * The bar is revealed first and without waiting for the animation, because neither route came
     * from a user scroll and the direction connection would otherwise leave the list sitting at its
     * top with no bar — the one position from which nothing can be reached.
     */
    val scope = rememberCoroutineScope()
    suspend fun scrollToTop() {
        navigationBarScrollConnection.reveal()
        revealTopBar()
        // Animating hundreds of rows past would take seconds and show nothing. Jumping to the last
        // screenful first costs the same gesture a fixed, short animation however deep the user is.
        if (listState.firstVisibleItemIndex > SCROLL_TO_TOP_ANIMATED_ITEMS) {
            listState.scrollToItem(SCROLL_TO_TOP_ANIMATED_ITEMS)
        }
        listState.animateScrollToItem(0)
    }
    /*
     * Which request has already been answered, remembered exactly as the board identity above is and
     * for the same reason: opening a thread takes this screen out of the composition, so a plain
     * `LaunchedEffect` on the count alone would run again on the way back and throw away the offset
     * the list had just restored. A tap that happened before the thread was opened is not a request
     * to scroll after returning from it.
     */
    var answeredScrollRequest by rememberSaveable { mutableIntStateOf(scrollToTopRequests) }
    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests != answeredScrollRequest) {
            answeredScrollRequest = scrollToTopRequests
            scrollToTop()
        }
    }

    val refreshState = posts.loadState.refresh
    val appendState = posts.loadState.append

    /*
     * 首页翻页栏 — off unless 设置 asks for it, and hidden while there is only one page to be on.
     *
     * It does not replace the scroll: pages still append as the reader reaches the foot, exactly as
     * they did before. What it adds is a way to *arrive* — the same pairing the comment thread and
     * 管理记录 use, and the reason the control is shared with them rather than written again here.
     */
    var showPageSheet by remember { mutableStateOf(false) }
    val showPageBar = state.pageBarEnabled && state.totalPages > 1 && posts.itemCount > 0

    /*
     * The page the reader is looking at, which on an appending list is not the page most recently
     * fetched. Held rather than derived because a row can be a placeholder: outside the loaded window
     * there is no page to read off, and the last real answer beats a guess.
     */
    var visiblePage by remember { mutableIntStateOf(state.startPage) }
    LaunchedEffect(posts, listState, state.startPage) {
        // A jump names its destination before the rows arrive, which is the one moment the reader is
        // watching the bar for confirmation that the tap landed.
        visiblePage = state.startPage
        snapshotFlow { listState.firstVisibleItemIndex to posts.itemCount }
            .collect { (index, count) ->
                if (index >= count) return@collect
                // Rows from before the jump are still on screen while the replacement loads. The
                // window can never begin before the page it was sent to, so anything earlier than
                // that is a leftover rather than an answer.
                posts.peek(index)?.page?.takeIf { it >= state.startPage }?.let { visiblePage = it }
            }
    }

    /**
     * 上一页 / 下一页 on a list that is one continuous scroll: the step is a scroll wherever it can be,
     * and only a page the feed has no way to reach by reading on is fetched as a new window.
     *
     * Three cases, in the order they are tried:
     *
     * The feed already holds the page — scroll to it. The row is very often a placeholder, and that
     * is the point: with placeholders on, a row the reader scrolled past keeps its index whether or
     * not Paging is still holding it in memory, and arriving there is what makes Paging fetch that
     * window back. Asking [LazyPagingItems] instead — which is what this did — asks whether the page
     * is in *memory*, and one step away it never is, so every step reloaded the feed from the network.
     *
     * The page right after the ones it holds — that is not somewhere to travel to, it is the rest of
     * this scroll. Reaching the foot is what asks the feed for it, exactly as scrolling there by hand
     * would, and when its rows land we carry on into them. Fetching it as a new window instead would
     * throw away every page above it to arrive at the one place the reader could have simply scrolled.
     *
     * Anything else — a jump, and a jump is what it gets.
     */
    fun goToPage(target: Int) {
        scope.launch {
            onFindPageRow(target)?.takeIf { it < posts.itemCount }?.let { index ->
                listState.animateScrollToItem(index)
                return@launch
            }
            // Contiguity is enough to tell "read on" from "jump": the stored pages run without gaps,
            // so a target whose predecessor is stored can only be the one past the end. Going the
            // other way never lands here — a stored predecessor would mean a stored target.
            val readsOn = posts.itemCount > 0 && target > FIRST_PAGE && onFindPageRow(target - 1) != null
            if (readsOn) {
                listState.animateScrollToItem(posts.itemCount - 1)
                val arrived =
                    withTimeoutOrNull(APPEND_WAIT_MILLIS) {
                        // itemCount is the row count Room reports, so it changes exactly when the
                        // appended page lands — one query per arrival rather than a poll.
                        snapshotFlow { posts.itemCount }.mapNotNull { onFindPageRow(target) }.first()
                    }
                if (arrived != null) {
                    listState.animateScrollToItem(arrived)
                    return@launch
                }
            }
            onGoToPage(target)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                HomeTopBar(
                    sort = state.sort,
                    onSortChange = onSortChange,
                    onTitleClick = { scope.launch { scrollToTop() } },
                    scrollBehavior = topBarScrollBehavior,
                )
                BoardStrip(
                    boards = state.boards,
                    parkedBoards = state.parkedBoards,
                    selectedSlug = state.categorySlug,
                    onBoardClick = onBoardClick,
                    onArrangementChange = onArrangementChange,
                )
            }
        },
        floatingActionButton = {
            // With 翻页栏 on, 发帖 moves *into* the toolbar's own FAB slot rather than standing beside
            // it — two floating things in the same corner is what Material's slot exists to prevent,
            // and it is the arrangement the thread already ships with its 回复 button.
            if (!showPageBar) {
                // Follow the same sticky direction state as the navigation bar. Stopping cannot
                // briefly flip this value, so the built-in extended-FAB animation gets one stable
                // target.
                ExtendedFloatingActionButton(
                    onClick = onCreatePost,
                    expanded = !navigationBarHidden,
                    icon = {
                        Icon(Icons.Default.Add, contentDescription = null)
                    },
                    text = { Text(stringResource(Res.string.action_create_post)) },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val showSkeleton = posts.itemCount == 0 && refreshState is LoadState.Loading

            // Crossfade rather than a hard swap: a skeleton that snaps to content flashes, and the
            // structure underneath is identical anyway, so there is nothing to animate but opacity.
            Crossfade(targetState = showSkeleton, label = "feed-skeleton") { skeleton ->
                when {
                    skeleton -> FeedSkeleton()

                    // An error only takes over the screen when there is nothing cached to show. With
                    // rows on screen the failure is not worth losing the content over.
                    posts.itemCount == 0 && refreshState is LoadState.Error -> {
                        val error = refreshState.error.toSiteError()
                        SiteErrorState(
                            error = error,
                            onRetry = posts::refresh,
                            // Both recoveries open a browser, but not the same page: a challenge is
                            // cleared on the list URL, a locked board on the sign-in page.
                            onOpenBrowser = onRecoverInBrowser,
                            onSignIn = onSignInClick,
                            boardTitle = state.selectedBoardTitle,
                            onBrowseElsewhere = { onBoardClick(null) }.takeIf { state.categorySlug != null },
                        )
                    }

                    posts.itemCount == 0 && refreshState is LoadState.NotLoading ->
                        EmptyFeedState(
                            onBrowseElsewhere = { onBoardClick(null) }.takeIf { state.categorySlug != null },
                        )

                    else ->
                        PullToRefreshBox(
                            isRefreshing = refreshState is LoadState.Loading,
                            onRefresh = posts::refresh,
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    // The direction detector goes outside the app bar's connection so
                                    // it reads the raw gesture. Nested first, the app bar would eat
                                    // the first 64dp of every downward scroll into its own collapse
                                    // and the navigation bar would only start hiding afterwards.
                                    .nestedScroll(navigationBarScrollConnection)
                                    .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
                                    .readableWidth(),
                            ) {
                                items(
                                    count = posts.itemCount,
                                    key = posts.itemKey { it.summary.postId },
                                ) { index ->
                                    // Null is a counted row outside the loaded window, not a missing
                                    // one. Skipping it would collapse the space it is holding and
                                    // undo the stable indices placeholders were turned on for.
                                    when (val post = posts[index]) {
                                        null -> FeedRowPlaceholder()

                                        else -> {
                                            PostRow(
                                                post = post,
                                                onClick = { onPostClick(post) },
                                                sharedWithThread = true,
                                            )
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                            )
                                        }
                                    }
                                }
                                if (appendState is LoadState.Loading) {
                                    item(key = "append-spinner") { AppendSpinner() }
                                }
                            }
                        }
                }
            }

            if (showPageBar) {
                FeedPageBar(
                    // The same sticky direction state the FAB follows, rather than a second nested
                    // scroll connection of the toolbar's own: two of them reading the same gesture
                    // would disagree at the threshold and the bar would collapse out of step with
                    // the navigation bar it sits above.
                    expanded = !navigationBarHidden || showPageSheet,
                    page = visiblePage,
                    totalPages = state.totalPages,
                    onPrevious = { goToPage((visiblePage - 1).coerceAtLeast(1)) },
                    onNext = { goToPage((visiblePage + 1).coerceAtMost(state.totalPages)) },
                    onPageClick = { showPageSheet = true },
                    onCreatePost = onCreatePost,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    if (showPageSheet) {
        PageJumpSheet(
            page = visiblePage,
            totalPages = state.totalPages,
            // The site's own page size rather than a count of what is loaded: on a feed the reader
            // scrolls through, "已载入 N 个帖子" is a number that only ever goes up and says nothing
            // about where any page is.
            note = stringResource(Res.string.feed_page_size_note, OfflineFirstPostRepository.NETWORK_PAGE_SIZE),
            // No 上次浏览 here, unlike the thread: the feed keeps no place across visits, and this
            // session's furthest page is not one — nothing records it, and inventing one from the
            // scroll would offer the page the reader is already on.
            newest =
            JumpDestination(
                label = stringResource(Res.string.page_jump_newest),
                icon = PlazaIcons.VerticalAlignTop,
                onGo = {
                    showPageSheet = false
                    goToPage(1)
                },
            ).takeIf { visiblePage > 1 },
            onDismiss = { showPageSheet = false },
            onGo = { target ->
                showPageSheet = false
                goToPage(target.coerceIn(1, state.totalPages.coerceAtLeast(1)))
            },
        )
    }
}

/**
 * 首页翻页栏: the page keys stacked over 发帖, in the corner the thumb is already in.
 *
 * The rail itself is [PageJumpRail], shared with the comment thread and 管理记录 so the wording and
 * the shortcuts cannot drift between the three screens that have it. 发帖 leaves the `Scaffold`'s
 * FAB slot while this is on and joins the stack instead — two floating things side by side in one
 * corner is what that slot exists to prevent, and the thread has shipped the stacked pair for as
 * long as it has had a rail.
 */
@Composable
private fun FeedPageBar(
    expanded: Boolean,
    page: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageClick: () -> Unit,
    onCreatePost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.End,
        // The thread's measurement, for the thread's reason: the rail's bottom key carries 4dp of
        // touch-target slack under its paint, so 4dp here draws as the design's 8dp.
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PageJumpRail(
            expanded = expanded,
            page = page,
            totalPages = totalPages,
            onPrevious = onPrevious,
            onNext = onNext,
            onPageClick = onPageClick,
        )
        ExtendedFloatingActionButton(
            text = { Text(stringResource(Res.string.action_create_post)) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = onCreatePost,
            expanded = expanded,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * Which feed the rows on screen belong to — the board, the order and the page it starts at, as one
 * saveable value.
 *
 * The start page counts because a jump replaces every row: the offset that was restored belonged to
 * a window that no longer exists, so keeping it would land the reader in the middle of page 40.
 *
 * A plain string rather than the pair itself so it goes into a `Bundle` unchanged, and so comparing
 * "the same feed as before" cannot depend on how a null slug or an enum happens to be stored.
 */
private fun feedIdentity(state: PostListUiState): String =
    "${state.categorySlug.orEmpty()}/${state.sort.name}/${state.startPage}"

/** How much of the feed a "back to the top" actually animates past; anything beyond it is a jump. */
private const val SCROLL_TO_TOP_ANIMATED_ITEMS = 12

/** The first of the site's pages, and the one 上一页 can never step below. */
private const val FIRST_PAGE = 1

/**
 * How long 下一页 waits at the foot for the page it asked the feed to append.
 *
 * Generous on purpose: it is a request over a network the reader may be on a train with, and the
 * cost of giving up early is a window reload that throws away everything above. Giving up at all is
 * only for the load that failed outright — the append shows its own spinner and its own retry
 * meanwhile, so nothing about the wait is invisible.
 */
private const val APPEND_WAIT_MILLIS = 15_000L

/**
 * The home app bar carries the wordmark and exactly one action.
 *
 * Account and search both have their own tab at the bottom, so putting them up here as well would be
 * two ways to reach the same place. Sort has nowhere else to live.
 *
 * [scrollBehavior] folds the whole row away as the feed advances. It is measured out of the layout
 * rather than merely hidden, which is what lets the board strip below it ride up into the space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    sort: FeedSort,
    onSortChange: (FeedSort) -> Unit,
    onTitleClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = appName(),
                style = MaterialTheme.typography.titleLarge,
                // The wordmark doubles as the second way back to the top, and the only one that
                // works while the navigation bar is hidden — which is exactly when a reader deep in
                // the feed wants it. Height rather than padding grows the target to 48dp, so the
                // title stays on the same start inset as the board strip below it.
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        onClickLabel = stringResource(Res.string.action_scroll_to_top),
                        onClick = onTitleClick,
                    ).heightIn(min = Sizes.minTouchTarget)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = PlazaIcons.SwapVert,
                        contentDescription = stringResource(Res.string.action_sort),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SortMenuItem(Res.string.sort_by_reply_time, FeedSort.LAST_REPLY, sort) {
                        onSortChange(it)
                        menuOpen = false
                    }
                    SortMenuItem(Res.string.sort_by_post_time, FeedSort.POST_TIME, sort) {
                        onSortChange(it)
                        menuOpen = false
                    }
                }
            }
        },
        colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            // The default scrolled container tints the bar as it collapses. Here the strip below it
            // keeps the same surface throughout, and a bar that darkened on its way out would draw a
            // band across the top of the screen that then vanished.
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SortMenuItem(
    labelRes: StringResource,
    value: FeedSort,
    current: FeedSort,
    onClick: (FeedSort) -> Unit,
) {
    val isCurrent = value == current
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = { onClick(value) },
        // Which order is in force was carried entirely by the tick, and a decorative tick is not
        // information: TalkBack read the two items identically. `selected` is what puts "已选中"
        // into the announcement, so the icon can stay decoration.
        modifier = Modifier.semantics { selected = isCurrent },
        trailingIcon = {
            if (isCurrent) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * One topic.
 *
 * The title is the only thing with visual weight; everything else is a 12sp meta line under it. That
 * is the whole design of this list — nine of these fit on a 800dp screen, and the title is legible in
 * every one of them.
 */
@Composable
internal fun PostRow(
    post: FeedPost,
    onClick: () -> Unit,
    highlight: String? = null,
    /**
     * Off on 推荐阅读, where every row carries the badge and so it distinguishes nothing — the screen's
     * own title already says what the whole list is.
     */
    showAwardBadge: Boolean = true,
    /**
     * Whether this row's title, avatar, author and board tag are the same objects as the thread's,
     * and should travel there rather than cut — see [LocalThreadTransition].
     *
     * On for 首页 alone, which is the only list that hands the thread everything it would need to
     * draw them. A row that flew its contents into a thread arriving by the ordinary slide would
     * look worse than one that did not fly at all, and two lists claiming one post at the same
     * moment is a state the shared-element machinery has no answer for.
     */
    sharedWithThread: Boolean = false,
) {
    val summary = post.summary
    val avatarSize = listAvatarSize()
    ThreadRow(
        onClick = onClick,
        containerColor =
        if (summary.isPinned) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        leading = {
            if (summary.isPinned) {
                Box(
                    modifier =
                    Modifier
                        .offset(y = AvatarCapOffset)
                        .size(avatarSize)
                        .clip(AvatarShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PlazaIcons.PushPin,
                        contentDescription = stringResource(Res.string.post_badge_pinned),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                UserAvatar(
                    url = summary.avatarUrl,
                    name = summary.authorName,
                    size = avatarSize,
                    // The offset goes first so that what travels is where the avatar is actually
                    // placed, cap line and all, rather than where it would sit without it.
                    modifier = Modifier
                        .offset(y = AvatarCapOffset)
                        .thenIf(sharedWithThread) { Modifier.sharedThreadAvatar(summary.postId) },
                )
            }
        },
        title = {
            ThreadRowTitle(
                text = highlighted(summary.title, highlight, MaterialTheme.colorScheme.primary),
                // A read thread is dimmed rather than hidden — the list is still the user's history,
                // and the drop in both weight and contrast is legible at a glance without adding a
                // badge that would cost a row's worth of space.
                color =
                if (post.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (post.isRead) FontWeight.Medium else FontWeight.SemiBold,
                // Not filling, so the lock beside it keeps its place instead of being pushed off.
                // The shared bounds go inside the weight, so what travels is the title as the row
                // actually lays it out rather than as it would be unconstrained.
                modifier =
                Modifier
                    .weight(1f, fill = false)
                    .thenIf(sharedWithThread) { Modifier.sharedThreadTitle(summary.postId) },
            )
            if (summary.isLocked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription =
                    summary.lockLevel
                        ?.let { stringResource(Res.string.post_badge_locked_level, it) }
                        ?: stringResource(Res.string.post_badge_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 16dp per the b1 §8 只读→锁定 mapping spec, in sp so it tracks the title
                    // it stands beside rather than shrinking against a raised reading size.
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(textScaledSize(TITLE_BADGE_SIZE)),
                )
                summary.lockLevel?.let { level ->
                    Text(
                        text = level.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showAwardBadge && summary.isAwarded) {
                Icon(
                    NodeSeekIcons.Award,
                    contentDescription = stringResource(Res.string.post_badge_awarded),
                    // The warm role rather than primary: 加精 is a mark the site puts on a thread, not
                    // an action this app offers, and the site draws it orange. Same 16dp as the lock,
                    // and after it — the order the site's own title strip uses.
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(textScaledSize(TITLE_BADGE_SIZE)),
                )
            }
        },
        meta = { PostMetaItems(post, sharedWithThread) },
    )
}

/** The lock and the 加精 mark beside a title, at the size the b1 §8 badge spec gives them. */
private val TITLE_BADGE_SIZE = 16.sp

/**
 * Applies [modifier] only when [condition] holds.
 *
 * A helper because the four shared-element modifiers below are all conditional on the same flag, and
 * spelling out `if (flag) Modifier.x() else Modifier` four times buried the row's layout under the
 * animation's bookkeeping. Composable-aware, which is why it is a lambda and not a value.
 */
@Composable
private inline fun Modifier.thenIf(
    condition: Boolean,
    modifier: @Composable () -> Modifier,
): Modifier = if (condition) then(modifier()) else this

/**
 * The searched-for words picked out of the title.
 *
 * Literal and case-insensitive, because that is what the search itself is: the site matches the raw
 * string, so a cleverer match here would paint a word the results were not chosen for. Returns the
 * plain title when nothing is being searched, which is every list but the search results.
 */
internal fun highlighted(
    title: String,
    query: String?,
    color: Color,
): AnnotatedString {
    val needle = query?.trim().orEmpty()
    if (needle.isEmpty() || !title.contains(needle, ignoreCase = true)) return AnnotatedString(title)
    return buildAnnotatedString {
        var cursor = 0
        while (true) {
            val match = title.indexOf(needle, cursor, ignoreCase = true)
            if (match < 0) break
            append(title, cursor, match)
            withStyle(SpanStyle(color = color)) { append(title, match, match + needle.length) }
            cursor = match + needle.length
        }
        append(title, cursor, title.length)
    }
}

/**
 * The meta line, in the order a scanning eye wants it: what board, who, how busy, how fresh.
 *
 * The row itself belongs to [ThreadRow] — this only says what goes in it.
 *
 * Two rules keep it on one line, which is the whole point of a meta line: the counts are icons
 * rather than words (see [MetaStat]), and the author — the one item with no upper bound on its
 * length — is the item that gives way. Everything else is a handful of characters wide, so a long
 * name ellipsizing is the only thing that has to happen for the timestamp to keep its place.
 */
@Composable
private fun FlowRowScope.PostMetaItems(
    post: FeedPost,
    sharedWithThread: Boolean = false,
) {
    val summary = post.summary
    BoardTag(
        title = summary.categoryTitle,
        slug = summary.categorySlug,
        modifier = Modifier.thenIf(sharedWithThread) { Modifier.sharedThreadBoard(summary.postId) },
    )
    MetaText(
        summary.authorName,
        singleLine = true,
        modifier =
        Modifier
            // fill = false so a short name stays short and the counts sit next to it, rather than
            // being pushed to the right edge with a lake of space in between.
            .weight(1f, fill = false)
            .thenIf(sharedWithThread) { Modifier.sharedThreadAuthor(summary.postId) },
    )
    if (summary.isPinned) MetaText(stringResource(Res.string.post_badge_pinned), singleLine = true)
    if (post.newCommentCount > 0) {
        NewReplyBadge(post.newCommentCount)
    } else {
        summary.commentCount?.let {
            MetaStat(
                icon = PlazaIcons.ModeComment,
                value = it.toString(),
                contentDescription = stringResource(Res.string.post_reply_count, it),
            )
        }
    }
    summary.viewCount?.let {
        MetaStat(
            icon = PlazaIcons.Visibility,
            value = it.toString(),
            contentDescription = stringResource(Res.string.post_view_count, it),
        )
    }
    summary.lastActiveText?.let { MetaText(it, singleLine = true) }
}

/** Replaces the reply count once the user has read the thread: the delta is the useful number. */
@Composable
private fun NewReplyBadge(count: Int) {
    Text(
        text = stringResource(Res.string.post_new_reply_count, count),
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/**
 * First-load placeholder, laid out row for row like the real list.
 *
 * A spinner tells the user to wait; a skeleton with the right shape tells them what is coming and
 * measurably feels faster for a list whose structure never varies.
 */
@Composable
private fun FeedSkeleton(modifier: Modifier = Modifier) {
    val widths = listOf(0.88f, 0.68f, 0.94f, 0.75f, 0.84f, 0.62f, 0.90f, 0.71f, 0.80f)
    val metaWidths = listOf(0.52f, 0.44f, 0.57f, 0.40f, 0.49f, 0.46f, 0.38f, 0.53f, 0.45f)

    Column(modifier.fillMaxSize()) {
        widths.forEachIndexed { index, width ->
            FeedRowPlaceholder(titleFraction = width, metaFraction = metaWidths[index])
        }
    }
}

/**
 * One row's worth of skeleton, used both by the first-load [FeedSkeleton] and for a paging
 * placeholder — a row the database has counted but has not handed to the window yet.
 *
 * The two have to be the same shape. A placeholder that measured differently from the row replacing
 * it would move everything below it the moment the real row arrived, which is the same jump the
 * placeholders exist to prevent.
 */
@Composable
internal fun FeedRowPlaceholder(
    titleFraction: Float = 0.82f,
    metaFraction: Float = 0.48f,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(listAvatarSize())
                .clip(AvatarShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SkeletonBar(fraction = titleFraction, height = 13.dp)
            SkeletonBar(fraction = metaFraction, height = 11.dp)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// -------------------------------------------------------------------------------------------------
// Previews use the real scraped rows from the design doc rather than lorem ipsum: the layout has to
// survive a 40-character Chinese title next to a two-character one, and made-up copy never proves it.
// -------------------------------------------------------------------------------------------------

internal val previewState =
    PostListUiState(
        boards =
        listOf(
            Board(null, "综合", null),
            Board("daily", "日常", null),
            Board("tech", "技术", null),
            Board("info", "情报", null),
            Board("review", "测评", null),
            Board("trade", "交易", null),
            Board("dev", "Dev", null),
            Board("inside", "内版", null, adminOnly = true),
        ),
    )

private fun summary(
    id: Long,
    title: String,
    author: String,
    board: String,
    slug: String,
    replies: Int?,
    views: Int?,
    time: String,
    pinned: Boolean = false,
    locked: Boolean = false,
) = PostSummary(
    postId = id,
    title = title,
    authorName = author,
    authorUid = id,
    avatarUrl = null,
    categoryTitle = board,
    categorySlug = slug,
    viewCount = views,
    commentCount = replies,
    lastActiveText = time,
    lastActiveTitle = null,
    isPinned = pinned,
    isLocked = locked,
)

internal fun previewFeed(): Flow<PagingData<FeedPost>> =
    flowOf(
        PagingData.from(
            listOf(
                FeedPost(
                    summary(
                        0, "【公告】NodeSeek 社区规则与常见问题", "admin", "综合", "front",
                        null, null, "", pinned = true,
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(
                        1,
                        "iLatency公测，一个新的社区内Ping站，邀你共建",
                        "酒神",
                        "Dev",
                        "dev",
                        340,
                        30594,
                        "28分钟前",
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(
                        2,
                        "移动说回馈老用户，十一年宽带合约，一次性交3年宽带费，后续8年不缴费，还送一部手机",
                        "宝宝困困",
                        "日常",
                        "daily",
                        53,
                        1551,
                        "11秒前",
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                // Read, with replies since — the state the read-mark table exists to render.
                FeedPost(
                    summary(3, "为什么codex 还没重置呢?", "bigxiang", "日常", "daily", 6, 89, "30秒前"),
                    isRead = true,
                    newCommentCount = 4,
                ),
                FeedPost(
                    summary(
                        4,
                        "【出】剩余价值包push出JP.TKY.TRI.Basic",
                        "demain",
                        "交易",
                        "trade",
                        6,
                        124,
                        "20秒前",
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(5, "收北京腾讯云无忧235", "jswcph", "交易", "trade", 0, 2, "3秒前"),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(6, "暗黑模式下热榜显示不清(样式)", "wh1te", "Dev", "dev", 1, 21, "28秒前"),
                    isRead = true,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(
                        7, "可恶，咋我就是原价订阅啊", "深蓝色的天", "日常", "daily",
                        11, 379, "11秒前", locked = true,
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(8, "曝光某商家跑路", "anon", "曝光", "expose", 22, 900, "1分钟前"),
                    isRead = false,
                    newCommentCount = 0,
                ),
            ),
        ),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post list")
@Composable
private fun PostListScreenPreview() {
    PlazaTheme {
        PostListScreen(
            state = previewState,
            posts = previewFeed().collectAsLazyPagingItems(),
            onPostClick = {},
            onBoardClick = {},
            onSortChange = {},
            onSignInClick = {},
            onRecoverInBrowser = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post list · dark")
@Composable
private fun PostListScreenDarkPreview() {
    PlazaTheme(darkTheme = true) {
        PostListScreen(
            state = previewState,
            posts = previewFeed().collectAsLazyPagingItems(),
            onPostClick = {},
            onBoardClick = {},
            onSortChange = {},
            onSignInClick = {},
            onRecoverInBrowser = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post list · skeleton")
@Composable
private fun PostListSkeletonPreview() {
    PlazaTheme { FeedSkeleton() }
}
