package io.github.nodyssey.ui.postlist

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.Site
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.EmptyFeedState
import io.github.nodyssey.ui.common.JumpDestination
import io.github.nodyssey.ui.common.LocalThreadTransition
import io.github.nodyssey.ui.common.LockBadge
import io.github.nodyssey.ui.common.NavigationBarScrollConnection
import io.github.nodyssey.ui.common.NavigationDirectionThreshold
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.PageJumpRail
import io.github.nodyssey.ui.common.PageJumpSheet
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.TITLE_BADGE_SIZE
import io.github.nodyssey.ui.common.lockBadgeDescription
import io.github.nodyssey.ui.common.sharedThreadAuthor
import io.github.nodyssey.ui.common.sharedThreadAvatar
import io.github.nodyssey.ui.common.sharedThreadBoard
import io.github.nodyssey.ui.common.sharedThreadTitle
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.common.siteErrorRecovery
import io.github.nodyssey.ui.common.snackbarDuration
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_create_post
import io.github.nodyssey.ui.resources.action_search
import io.github.nodyssey.ui.resources.action_sort
import io.github.nodyssey.ui.resources.action_switch_site
import io.github.nodyssey.ui.resources.feed_page_size_note
import io.github.nodyssey.ui.resources.page_jump_newest
import io.github.nodyssey.ui.resources.post_badge_awarded
import io.github.nodyssey.ui.resources.post_badge_pinned
import io.github.nodyssey.ui.resources.post_new_reply_count
import io.github.nodyssey.ui.resources.post_reply_count
import io.github.nodyssey.ui.resources.post_view_count
import io.github.nodyssey.ui.resources.site_switch_next_launch
import io.github.nodyssey.ui.resources.sort_by_post_time
import io.github.nodyssey.ui.resources.sort_by_reply_time
import io.github.nodyssey.ui.settings.rememberSiteSwitch
import io.github.nodyssey.ui.settings.siteSwitchRestartsApp
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.AvatarCapOffset
import io.github.plaza.designsys.component.AvatarShape
import io.github.plaza.designsys.component.MetaStat
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PrefetchAvatars
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.ThreadRow
import io.github.plaza.designsys.component.ThreadRowTitle
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.listAvatarSize
import io.github.plaza.designsys.component.textScaledSize
import io.github.plaza.designsys.theme.PlazaTheme
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
import kotlin.math.abs

/**
 * Stateful entry point. It only wires the ViewModel to the stateless [PostListScreen] below, which is
 * what keeps the screen previewable and testable without a running app.
 */
@Composable
fun PostListRoute(
    viewModel: PostListViewModel,
    feedStates: HomeFeedStates,
    onPostClick: (FeedPost) -> Unit,
    onCreatePost: () -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
    reselectRequests: Int = 0,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PostListScreen(
        state = state,
        // One stream per board, so the page sliding in beside the selected one has rows of its own.
        postsFor = { slug -> viewModel.feed(slug).collectAsLazyPagingItems() },
        feedStates = feedStates,
        onPostClick = onPostClick,
        onCreatePost = onCreatePost,
        onSearch = onSearch,
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
        reselectRequests = reselectRequests,
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
    /**
     * One board's rows.
     *
     * A function rather than a list because the pager decides how many boards are on screen: the
     * selected one always, and the one coming in beside it for as long as a swipe is in flight.
     */
    postsFor: @Composable (String?) -> LazyPagingItems<FeedPost>,
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
     * database rather than by [postsFor], which holds one window and calls everything outside it
     * absent.
     */
    onFindPageRow: suspend (Int) -> Int? = { null },
    /** Where each board was last left off; see [HomeFeedStates] for who owns it and why. */
    feedStates: HomeFeedStates = rememberSaveable(saver = HomeFeedStates.Saver) { HomeFeedStates() },
    /** Commits an edit made on the board strip itself: the pill order, and which boards are parked. */
    onArrangementChange: (order: List<String>, parked: Set<String>) -> Unit = { _, _ -> },
    onCreatePost: () -> Unit = {},
    /** Opens 搜索 on top of this list, from the app bar's own action. */
    onSearch: () -> Unit = {},
    /** Keeps the host navigation bar hidden until the user deliberately scrolls toward the list start. */
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
    /**
     * How many times the host has reported a tap on the already-selected 首页 tab. Any increase
     * reloads the feed and returns to its first row; the count itself means nothing, which is what
     * lets two taps in a row read as two requests without the host having to clear a flag afterwards.
     */
    reselectRequests: Int = 0,
) {
    /*
     * 左右滑动切换板块.
     *
     * The boards are pages of one pager, in the order the strip draws them, so a board arrives with
     * the finger rather than after it: the neighbour is composed and paging while the gesture is
     * still in flight, and a swipe that changes its mind halfway simply slides back.
     *
     * The pager is also what decides the gesture, and it is worth being explicit that this is *not*
     * hand-rolled: a `LazyColumn` inside claims a drag that crosses its own slop vertically first,
     * and from that moment the pager sees every change as consumed and cannot start. Scrolling and
     * paging therefore cannot both happen in one gesture, whichever way the thumb curves afterwards.
     */
    val boards = state.boards
    val selectedIndex = boards.indexOfFirst { it.slug == state.categorySlug }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { boards.size }

    /*
     * The selected board's rows and its place in them: what the chrome around the pager is about —
     * 翻页栏, the stale-refresh snackbar, the 首页 tab's own "back to the top".
     *
     * The list state is the very object the selected page draws with; the rows are a second reader
     * of the same cached stream, and deliberately so. A page keeps the one presenter it was composed
     * with for as long as it exists, rather than being handed this one whenever the selection lands
     * on it: swapping a presenter under a live `LazyColumn` empties it for a frame, and an empty
     * list is a list whose scroll position has been clamped to the top. This copy reads the same
     * pages out of the same cache — `cachedIn` seeds a new presenter with them — and asks the
     * network for nothing; see [PostListViewModel.feed].
     */
    val posts = postsFor(state.categorySlug)
    val listState = feedStates.listState(state.categorySlug, state.sort)

    /*
     * The swipe reports its answer once, when the gesture comes to rest. Everything before that is a
     * gesture still being made, and a board selected halfway through one would reload the strip, the
     * page count and 翻页栏 under a finger that had not decided anything yet.
     *
     * Read through [rememberUpdatedState] rather than keyed on the boards: the strip commits a
     * reorder while the finger is still down, and restarting this collector would re-answer a
     * settled page whose index now names a different board.
     */
    val currentBoards by rememberUpdatedState(boards)
    val currentSlug by rememberUpdatedState(state.categorySlug)
    val currentOnBoardClick by rememberUpdatedState(onBoardClick)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            // Coming to rest where the selection already is says nothing — that is every first
            // composition, and every page this effect was the one to move.
            currentBoards.getOrNull(page)?.takeIf { it.slug != currentSlug }?.let {
                currentOnBoardClick(it.slug)
            }
        }
    }

    /*
     * The other direction: a pill tapped, a board that stopped being selectable, a selection restored
     * with the screen. Only a step to the immediate neighbour is animated — a tap on a distant pill
     * would otherwise fly through every board between the two, fetching each on the way past.
     */
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage == selectedIndex) return@LaunchedEffect
        if (abs(pagerState.currentPage - selectedIndex) == 1) {
            pagerState.animateScrollToPage(selectedIndex)
        } else {
            pagerState.scrollToPage(selectedIndex)
        }
    }

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
     * The 排序 / 搜索 row rides the scroll; the board strip under it does not.
     *
     * Only the app bar is given a scroll behaviour, so the strip — which is navigation, and the one
     * thing a reader reaches for mid-feed — stays put while 排序 and 搜索 fold away. `enterAlways`
     * rather than the navigation bar's sticky threshold: the bar is what those two live in, and a
     * short drag back up has to be enough to reach them.
     */
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    /**
     * Unfolds the app bar without a gesture, for the jumps that put the list back at its first row.
     *
     * The bar collapses only against scroll deltas it has consumed, so a programmatic scroll leaves it
     * folded at the top of the feed — the one place there is nothing left to scroll back up through.
     */
    fun revealTopBar() {
        topBarScrollBehavior.state.heightOffset = 0f
        topBarScrollBehavior.state.contentOffset = 0f
    }

    /*
     * Arriving somewhere the reader did not scroll to unfolds the app bar, and a jump also throws the
     * offset away.
     *
     * Two arrivals qualify. A new 排序 is a different feed drawn on a fresh set of list states, so
     * every board starts at its top. A jump is 翻页栏 sending *this* board somewhere: the rows are
     * replaced under the very list state that is holding an offset into the old ones. Both land the
     * reader at the head of a list without a gesture, and the bar folds only against deltas it has
     * consumed — a programmatic arrival produces none, so it would sit folded over a list with
     * nothing left to scroll back up through.
     *
     * A board is not one of those. It keeps its own place in its own list, so switching to one is an
     * arrival at exactly where that board was left — the app bar the reader had folded away included.
     * Unfolding it there made every swipe throw the bar back out over the rows.
     *
     * Remembered across leaving the composition, because opening a thread takes this screen out of it
     * and a plain `LaunchedEffect` would otherwise re-run on the way back and throw away the offset
     * the list had just restored.
     */
    var lastSlug by rememberSaveable { mutableStateOf(state.categorySlug.orEmpty()) }
    var lastSort by rememberSaveable { mutableStateOf(state.sort.name) }
    var lastStartPage by rememberSaveable { mutableIntStateOf(state.startPage) }
    LaunchedEffect(state.categorySlug, state.sort, state.startPage) {
        val slug = state.categorySlug.orEmpty()
        val sortChanged = state.sort.name != lastSort
        // A jump moves the board the reader is on; a different board arrives with a page of its own.
        val jumped = !sortChanged && slug == lastSlug && state.startPage != lastStartPage
        lastSlug = slug
        lastSort = state.sort.name
        lastStartPage = state.startPage
        if (!sortChanged && !jumped) return@LaunchedEffect
        revealTopBar()
        if (jumped) listState.scrollToItem(0)
    }

    // The host must not stay stuck bar-less if this screen leaves the composition.
    DisposableEffect(Unit) { onDispose { currentOnNavigationBarHiddenChanged(false) } }

    /*
     * Back to the start of the feed. Only the 首页 tab asks for this now — the wordmark that used to
     * be the second way in is gone with the rest of the title.
     *
     * The bar is revealed first and without waiting for the animation, because the request did not
     * come from a user scroll and the direction connection would otherwise leave the list sitting at
     * its top with no bar — the one position from which nothing can be reached.
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
    var answeredRequest by rememberSaveable { mutableIntStateOf(reselectRequests) }
    LaunchedEffect(reselectRequests) {
        if (reselectRequests != answeredRequest) {
            answeredRequest = reselectRequests
            /*
             * Reload first, then travel. `refresh` returns at once and only flips the load state, so
             * the pull-to-refresh spinner is already turning while the list animates back — asking
             * for it after the animation would leave the tap looking unanswered for its whole length.
             */
            posts.refresh()
            scrollToTop()
        }
    }

    val refreshState = posts.loadState.refresh

    /*
     * A refresh that failed onto rows already on screen.
     *
     * The full-screen error below deliberately does not take this case — losing the cached feed over
     * a failed reload is worse than keeping it. But saying nothing at all leaves the reader pulling a
     * list that simply does not move, with no way to tell a quiet network from a quiet forum. It cost
     * a debugging session (2026-08-20: 私人 DNS 设成主机名, that DoT server unreachable, so every
     * refresh threw `UnknownHostException` and the screen looked merely stale).
     *
     * Keyed on the message: a refresh starting clears it to null, which cancels the snackbar the way
     * a new attempt should, and the next failure re-raises it even when it says the same thing.
     *
     * The button is [siteErrorRecovery]'s, not a hardcoded 重试. This strip is the *only* thing a
     * reader with a cached feed is shown — the full-screen state below takes over when there is
     * nothing cached — so 重试 on a Cloudflare wall left them pressing a button that earns the same
     * wall every time, with the web view that would clear it on a screen they never reached.
     */
    val snackbarHostState = remember { SnackbarHostState() }
    val staleRefreshError =
        (refreshState as? LoadState.Error)
            ?.takeIf { posts.itemCount > 0 }
            ?.error
            ?.toSiteError()
    val staleRefreshMessage = staleRefreshError?.shortMessage()
    val staleRefreshAction =
        staleRefreshError?.let { error ->
            siteErrorRecovery(
                error = error,
                onVerify = onRecoverInBrowser,
                onSignIn = onSignInClick,
                onRetry = posts::refresh,
            )
        }
    LaunchedEffect(staleRefreshMessage) {
        val error = staleRefreshError ?: return@LaunchedEffect
        val message = staleRefreshMessage ?: return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = staleRefreshAction?.label,
                duration = snackbarDuration(error),
            )
        if (result == SnackbarResult.ActionPerformed) staleRefreshAction?.onClick?.invoke()
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                HomeTopBar(
                    sort = state.sort,
                    onSortChange = onSortChange,
                    onSearch = onSearch,
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
            HorizontalPager(
                state = pagerState,
                // Keyed by board, so parking one or dragging it up the strip moves its page with it
                // rather than handing its rows to whichever board inherited the index.
                key = { index -> boards.getOrNull(index)?.slug ?: FRONT_PAGE_KEY },
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val board = boards.getOrNull(index) ?: return@HorizontalPager
                BoardFeed(
                    board = board,
                    posts = postsFor(board.slug),
                    listState = feedStates.listState(board.slug, state.sort),
                    onPostClick = onPostClick,
                    onSignInClick = onSignInClick,
                    onRecoverInBrowser = onRecoverInBrowser,
                    onBrowseElsewhere = { onBoardClick(null) },
                    navigationBarScrollConnection = navigationBarScrollConnection,
                    topBarScrollBehavior = topBarScrollBehavior,
                )
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
 * One board, as one page of the pager: its rows, or whatever it has instead of them.
 *
 * Every state a feed can be in lives here rather than around the pager, because they are all about
 * one board — a board that is still loading, one that answered with a Cloudflare wall, one that is
 * empty — and the neighbour on screen beside it may be in a different one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardFeed(
    board: Board,
    posts: LazyPagingItems<FeedPost>,
    listState: LazyListState,
    onPostClick: (FeedPost) -> Unit,
    onSignInClick: () -> Unit,
    onRecoverInBrowser: () -> Unit,
    onBrowseElsewhere: () -> Unit,
    navigationBarScrollConnection: NavigationBarScrollConnection,
    topBarScrollBehavior: TopAppBarScrollBehavior,
) {
    val refreshState = posts.loadState.refresh
    val appendState = posts.loadState.append
    val showSkeleton = posts.itemCount == 0 && refreshState is LoadState.Loading
    // 综合 is the front page rather than a board, so it is the one place "read something else"
    // cannot point at.
    val elsewhere = onBrowseElsewhere.takeIf { board.slug != null }

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
                    //
                    // 去验证 is named rather than left to fall back on [onOpenBrowser]. They
                    // happen to be the same closure here, and the fallback is what let other
                    // screens hand a challenge to a plain reading web view without anything
                    // saying so.
                    onOpenBrowser = onRecoverInBrowser,
                    onVerify = onRecoverInBrowser,
                    onSignIn = onSignInClick,
                    boardTitle = board.title.takeIf { board.slug != null },
                    onBrowseElsewhere = elsewhere,
                )
            }

            posts.itemCount == 0 && refreshState is LoadState.NotLoading ->
                EmptyFeedState(onBrowseElsewhere = elsewhere)

            else -> {
                // The rows carry their author's face, and one HTML page hands over fifty
                // addresses at once — but not fifty pictures. Without this the reader
                // scrolls onto a row and then waits for it; see [PrefetchAvatars].
                //
                // `peek` rather than `get`: asking for a row this far ahead must not be
                // read as the reader arriving there and pull the next page in early.
                PrefetchAvatars(
                    listState = listState,
                    itemCount = posts.itemCount,
                    size = listAvatarSize(),
                    urlAt = { index -> posts.peek(index)?.summary?.avatarUrl },
                )
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
 * The home app bar: 排序 at the start, 站点 in the middle, 搜索 at the end.
 *
 * The wordmark used to fill the middle and was removed: it named a screen the reader had just
 * arrived at from the navigation bar, which is the one thing an app bar title does not need to say,
 * and it doubled as 回到顶部 — a target nothing on screen described. What sits there now is not that
 * back. A second forum runs the same software ([Site]) and the app can be pointed at either, so
 * *which* one these rows came from is the one thing about this screen a reader cannot work out by
 * looking: the boards below differ, but 综合 looks like 综合 either way. It is also the control that
 * changes it, which is what earns a slot rather than a caption. 首页 still answers a second tap with
 * the same jump back to the first row.
 *
 * `navigationIcon` rather than a second action: 排序 changes what the list below *is*, 搜索 leaves it
 * for another list, and putting the two in one corner would make them look like a pair of filters.
 *
 * [scrollBehavior] folds the whole row away as the feed advances. It is measured out of the layout
 * rather than merely hidden, which is what lets the board strip below it ride up into the space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    sort: FeedSort,
    onSortChange: (FeedSort) -> Unit,
    onSearch: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = { SiteSwitcher() },
        navigationIcon = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = PlazaIcons.SwapVert,
                        contentDescription = stringResource(Res.string.action_sort),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Anchored to the button rather than to the bar, so the menu opens down the start
                // edge it was summoned from.
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
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.action_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/**
 * 站点: which forum the feed below came from, and the way to the other one.
 *
 * A text button rather than a title, because it is one. The site's own wordmark rather than a
 * translated label — NodeSeek and DeepFlood are what the sites call themselves in every language the
 * app ships.
 *
 * The menu is anchored to the button and centred with it. Both entries are always listed, the
 * current one ticked, which is the same shape 排序 uses two slots to the left; a reader who opens it
 * to check where they are should not have to change anything to find out.
 *
 * Switching restarts the app on Android — see [rememberSiteSwitch] for why nothing cheaper is
 * correct — and on a platform where it cannot, the menu says so instead of letting the reader
 * discover that nothing happened.
 */
@Composable
private fun SiteSwitcher() {
    var menuOpen by remember { mutableStateOf(false) }
    val active = ActiveSite.current
    val switchSite = rememberSiteSwitch()

    Box {
        TextButton(onClick = { menuOpen = true }) {
            Text(
                text = active.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                // The button already reads out the site's name; the arrow only says it opens.
                contentDescription = stringResource(Res.string.action_switch_site),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Site.entries.forEach { site ->
                val isCurrent = site == active
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(site.displayName)
                            // 下次启动生效 under the name it applies to rather than as a note
                            // elsewhere on the screen: the sentence is only true of this entry, and
                            // only on a platform that cannot restart itself.
                            if (!isCurrent && !siteSwitchRestartsApp) {
                                Text(
                                    text = stringResource(Res.string.site_switch_next_launch),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        menuOpen = false
                        switchSite(site)
                    },
                    // Same reason as [SortMenuItem]: without this the two entries are announced
                    // identically and the tick is decoration TalkBack cannot see.
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
        }
    }
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
                LockBadge(
                    level = summary.lockLevel,
                    description = lockBadgeDescription(summary.lockLevel),
                )
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
            postsFor = { previewFeed().collectAsLazyPagingItems() },
            onPostClick = {},
            onBoardClick = {},
            onSortChange = {},
            onSignInClick = {},
            onRecoverInBrowser = {},
        )
    }
}

// The review's F-13: every preview in the repository was 360dp, so a regression that only exists
// on a tablet-width window had nowhere to be seen before a device test. This is the list at the
// width where it shares the window with a detail pane.
@Preview(showBackground = true, widthDp = 840, heightDp = 800, name = "Post list · 840dp")
@Composable
private fun PostListScreenWidePreview() {
    PlazaTheme {
        PostListScreen(
            state = previewState,
            postsFor = { previewFeed().collectAsLazyPagingItems() },
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
            postsFor = { previewFeed().collectAsLazyPagingItems() },
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
