package io.github.nodyssey.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.common.PageJumpRail
import io.github.nodyssey.ui.common.PageJumpSheet
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.webViewUrl
import io.github.nodyssey.ui.postlist.PostRow
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.award_empty
import io.github.nodyssey.ui.resources.award_title
import io.github.nodyssey.ui.resources.feed_page_size_note
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.LayerCardGap
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

@Composable
fun AwardRoute(
    viewModel: AwardViewModel,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posts = viewModel.posts.collectAsLazyPagingItems()
    AwardScreen(
        state = state,
        posts = posts,
        onBack = onBack,
        onPostClick = onPostClick,
        onGoToPage = viewModel::goToPage,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

/**
 * 推荐阅读, read like 首页: the feed's own cards in one scroll, and the page rail in the corner.
 *
 * The row is the home feed's row, imported rather than reimplemented: this is the same kind of list,
 * and a second implementation would drift — read state, the meta line's order. The rail and its jump
 * sheet are the ones 首页, the thread and 管理记录 share; the site's numbered pager that sat under
 * the list before made every page a deliberate act, the next one included.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwardScreen(
    state: AwardUiState,
    posts: LazyPagingItems<AwardRow>,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    /** Starts the list afresh at a page; see [AwardViewModel.goToPage] for when this is the answer. */
    onGoToPage: (Int) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val appBarState = rememberOneHandAppBarState()
    var showPageSheet by remember { mutableStateOf(false) }
    var toolbarPinned by rememberSaveable { mutableStateOf(true) }

    /*
     * A page the reader jumped to whose rows are not on screen yet. The rail shows it straight away,
     * because the tap is the moment the reader looks there for confirmation, and the list is taken to
     * it once it lands — which is not the top of the list, since the page before it is fetched too.
     */
    var pendingPage by remember { mutableStateOf<Int?>(null) }

    fun indexOfPage(page: Int): Int? = posts.itemSnapshotList.indexOfFirst { it?.page == page }.takeIf { it >= 0 }

    val scrolledPage by remember(posts) {
        derivedStateOf {
            listState.firstVisibleItemIndex.takeIf { it < posts.itemCount }?.let { posts.peek(it)?.page }
        }
    }
    val visiblePage = pendingPage ?: scrolledPage ?: state.startPage

    LaunchedEffect(pendingPage, posts.itemSnapshotList) {
        val target = pendingPage ?: return@LaunchedEffect
        val index = indexOfPage(target) ?: return@LaunchedEffect
        listState.scrollToItem(index)
        pendingPage = null
    }

    /**
     * 上一页 / 下一页 and the sheet's keys: a scroll wherever the page can be reached by one, and a
     * new start only for a page nothing on screen leads to.
     *
     * A page already held is scrolled to. The page just past either end of what is held is the rest
     * of this scroll rather than somewhere to travel: the list is taken to that end, which is what
     * asks Paging for it, and on into its rows when they land. Starting the list over there instead
     * would throw away every page the reader has read to arrive where they could simply have scrolled.
     */
    fun goToPage(target: Int) {
        val page = target.coerceIn(1, state.totalPages.coerceAtLeast(1))
        // Every branch moves the list without a gesture, which the app bar never hears about, and the
        // page asked for would otherwise arrive in whatever is left under a bar still standing open.
        scope.launch { appBarState.fold() }
        scope.launch {
            indexOfPage(page)?.let { index ->
                listState.animateScrollToItem(index)
                return@launch
            }
            val held = posts.itemSnapshotList.mapNotNull { it?.page }
            val edge =
                when (page) {
                    held.maxOrNull()?.plus(1) -> posts.itemCount - 1
                    held.minOrNull()?.minus(1) -> 0
                    else -> null
                }
            if (edge != null) {
                listState.animateScrollToItem(edge)
                val arrived =
                    withTimeoutOrNull(EDGE_WAIT_MILLIS) { snapshotFlow { indexOfPage(page) }.filterNotNull().first() }
                if (arrived != null) {
                    listState.animateScrollToItem(arrived)
                    return@launch
                }
            }
            pendingPage = page
            onGoToPage(page)
        }
    }

    val refresh = posts.loadState.refresh
    val toolbarExpanded =
        toolbarPinned || !listState.canScrollBackward || showPageSheet || refresh is LoadState.Error

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.award_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        ) {
            when {
                posts.itemCount == 0 && refresh is LoadState.Loading -> LoadingState(Modifier.fillMaxSize())

                posts.itemCount == 0 && refresh is LoadState.Error -> {
                    val error = refresh.error.toSiteError()
                    SiteErrorState(
                        error = error,
                        onRetry = posts::retry,
                        // Named rather than reached by fallback; see the same note in `AssetsScreen`.
                        onOpenBrowser = {
                            onOpenBrowser(error.webViewUrl(NodeSeekSite.BASE_URL + NodeSeekSite.awardPath(visiblePage)))
                        },
                        onVerify = onOpenBrowser,
                        onSignIn = onSignIn,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                posts.itemCount == 0 ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.award_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else ->
                    // The feed's own cards, at the feed's gutter and gap: these are feed rows that
                    // happen to have been picked out, and they should read as the same objects.
                    LazyColumn(
                        state = listState,
                        contentPadding =
                        PaddingValues(
                            start = LayerPageGutter,
                            end = LayerPageGutter,
                            top = Spacing.sm,
                            bottom = RailClearance,
                        ),
                        verticalArrangement = Arrangement.spacedBy(LayerCardGap),
                        modifier = Modifier
                            .fillMaxSize()
                            .floatingToolbarVerticalNestedScroll(
                                expanded = toolbarExpanded,
                                onExpand = { toolbarPinned = true },
                                onCollapse = { toolbarPinned = false },
                            ),
                    ) {
                        // By page as well as by thread: a thread the site moves between pages while
                        // the reader scrolls can be served twice, and two rows may not share a key.
                        items(
                            count = posts.itemCount,
                            key = posts.itemKey { "${it.page}:${it.summary.postId}" },
                        ) { index ->
                            val row = posts[index] ?: return@items
                            val summary = row.summary
                            PostRow(
                                post =
                                FeedPost(
                                    summary = summary,
                                    isRead = summary.postId in state.readPostIds,
                                    newCommentCount = 0,
                                    page = row.page,
                                ),
                                onClick = { onPostClick(summary.postId) },
                                showAwardBadge = false,
                            )
                        }
                        when (posts.loadState.append) {
                            LoadState.Loading -> item("appending") { AppendSpinner(Modifier.describedAsLoading()) }

                            is LoadState.Error ->
                                item("append-failed") {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        TextButton(onClick = posts::retry) {
                                            Text(stringResource(Res.string.action_retry))
                                        }
                                    }
                                }

                            is LoadState.NotLoading -> Unit
                        }
                    }
            }

            if (posts.itemCount > 0) {
                // A jump, or the page before the one the list starts at, loading. Not a row at the head
                // of the list: an item there would shift every index the rail reads its page from.
                if (refresh is LoadState.Loading || posts.loadState.prepend is LoadState.Loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .describedAsLoading(),
                    )
                }
                // The same two failing, with rows still on screen to keep reading.
                if (refresh is LoadState.Error || posts.loadState.prepend is LoadState.Error) {
                    FilledTonalButton(
                        onClick = posts::retry,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = Spacing.sm),
                    ) {
                        Text(stringResource(Res.string.action_retry))
                    }
                }
            }

            if (posts.itemCount > 0 && state.totalPages > 1) {
                PageJumpRail(
                    expanded = toolbarExpanded,
                    page = visiblePage,
                    totalPages = state.totalPages,
                    onPrevious = { goToPage(visiblePage - 1) },
                    onNext = { goToPage(visiblePage + 1) },
                    onPageClick = { showPageSheet = true },
                    // No FAB under it, as on 管理记录: nothing is posted from here, so the rail is the
                    // whole of what floats and it sits where a FAB would.
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.lg),
                )
            }
        }
    }

    if (showPageSheet) {
        PageJumpSheet(
            page = visiblePage,
            totalPages = state.totalPages,
            // The site's page size, as 首页 says it: a count of what has loaded only ever goes up and
            // says nothing about where any page is.
            note = state.pageSize?.let { stringResource(Res.string.feed_page_size_note, it) }.orEmpty(),
            onDismiss = { showPageSheet = false },
            onGo = { target ->
                showPageSheet = false
                goToPage(target)
            },
        )
    }
}

/** Room under the last card for the rail folded down to its page key, and a little air past it. */
private val RailClearance = Spacing.lg + Sizes.minTouchTarget + Spacing.lg

/**
 * How long ↑ / ↓ at an end of the list waits for the adjoining page before starting afresh there.
 * Long, because the page is on its way the whole time and a fresh start would throw the scroll away.
 */
private const val EDGE_WAIT_MILLIS = 15_000L

// -------------------------------------------------------------------------------------------------

private fun previewSummary(
    id: Long,
    title: String,
    author: String,
    board: String,
    slug: String,
    replies: Int,
    views: Int,
    time: String,
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
)

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "9b 推荐阅读")
@Composable
private fun AwardPreview() {
    val rows =
        listOf(
            previewSummary(1, "写了个小工具，把 NodeSeek 的帖子同步到 RSS", "nssk", "Dev", "dev", 126, 8_432, "2天前"),
            previewSummary(2, "从零搭一套自用的探针与告警，踩坑全记录", "羽落无声", "技术", "tech", 88, 6_204, "3天前"),
            previewSummary(3, "2026 年低价 VPS 选购避坑指南", "ifreedom", "测评", "review", 243, 19_051, "上周"),
            previewSummary(4, "科普：为什么你的 IPv6 隧道延迟这么高", "jswcph", "技术", "tech", 57, 4_118, "上周"),
        ).map { AwardRow(it, page = 1) }
    val loaded = LoadState.NotLoading(endOfPaginationReached = false)
    val posts =
        remember { flowOf(PagingData.from(rows, LoadStates(loaded, loaded, loaded))) }.collectAsLazyPagingItems()
    PlazaTheme {
        AwardScreen(
            state = AwardUiState(totalPages = 18, pageSize = 20),
            posts = posts,
            onBack = {},
            onPostClick = {},
            onGoToPage = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}
