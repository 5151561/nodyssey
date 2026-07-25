package io.github.nsreader.ui.postlist

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.Board
import io.github.nsreader.data.FeedPost
import io.github.nsreader.model.FeedSort
import io.github.nsreader.model.PostSummary
import io.github.nsreader.ui.common.BoardTag
import io.github.nsreader.ui.common.EmptyFeedState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.TABULAR_FIGURES
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stateful entry point. It only wires the ViewModel to the stateless [PostListScreen] below, which is
 * what keeps the screen previewable and testable without a running app.
 */
@Composable
fun PostListRoute(
    viewModel: PostListViewModel,
    onPostClick: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PostListScreen(
        state = state,
        posts = viewModel.feed.collectAsLazyPagingItems(),
        onPostClick = onPostClick,
        onBoardClick = viewModel::selectCategory,
        onSortChange = viewModel::selectSort,
        onSignInClick = { onOpenBrowser(NodeSeekSite.BASE_URL + NodeSeekSite.SIGN_IN_PATH) },
        onRecoverInBrowser = { onOpenBrowser(viewModel.challengeUrl()) },
        modifier = modifier,
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
    onPostClick: (Long) -> Unit,
    onBoardClick: (String?) -> Unit,
    onSortChange: (FeedSort) -> Unit,
    onSignInClick: () -> Unit,
    onRecoverInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Switching boards is the one case where the previous scroll offset is meaningless.
    LaunchedEffect(state.categorySlug, state.sort) { listState.scrollToItem(0) }

    val refreshState = posts.loadState.refresh
    val appendState = posts.loadState.append

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                HomeTopBar(sort = state.sort, onSortChange = onSortChange)
                BoardStrip(
                    boards = state.boards,
                    selectedSlug = state.categorySlug,
                    onBoardClick = onBoardClick,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val showSkeleton = posts.itemCount == 0 && refreshState is LoadState.Loading

            // Crossfade rather than a hard swap: a skeleton that snaps to content flashes, and the
            // structure underneath is identical anyway, so there is nothing to animate but opacity.
            Crossfade(targetState = showSkeleton, label = "feed-skeleton") { skeleton ->
                when {
                    skeleton -> FeedSkeleton()

                    // An error only takes over the screen when there is nothing cached to show. With
                    // rows on screen the failure is not worth losing the content over.
                    posts.itemCount == 0 && refreshState is LoadState.Error -> {
                        val error = refreshState.error.toNodeSeekError()
                        NodeSeekErrorState(
                            error = error,
                            onRetry = posts::refresh,
                            // Both recoveries open a browser, but not the same page: a challenge is
                            // cleared on the list URL, a locked board on the sign-in page.
                            onOpenBrowser =
                            if (error == NodeSeekError.LoginRequired) onSignInClick else onRecoverInBrowser,
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
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(
                                    count = posts.itemCount,
                                    key = { index -> posts.peek(index)?.summary?.postId ?: index },
                                ) { index ->
                                    val post = posts[index]
                                    if (post != null) {
                                        PostRow(
                                            post = post,
                                            onClick = { onPostClick(post.summary.postId) },
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                                if (appendState is LoadState.Loading) {
                                    item(key = "append-spinner") {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(Spacing.lg),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(Modifier.size(22.dp))
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

/**
 * The home app bar carries the wordmark and exactly one action.
 *
 * Account and search both have their own tab at the bottom, so putting them up here as well would be
 * two ways to reach the same place. Sort has nowhere else to live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    sort: FeedSort,
    onSortChange: (FeedSort) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = NodeSeekIcons.SwapVert,
                        contentDescription = stringResource(R.string.action_sort),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SortMenuItem(R.string.sort_by_reply_time, FeedSort.LAST_REPLY, sort) {
                        onSortChange(it)
                        menuOpen = false
                    }
                    SortMenuItem(R.string.sort_by_post_time, FeedSort.POST_TIME, sort) {
                        onSortChange(it)
                        menuOpen = false
                    }
                }
            }
        },
        colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SortMenuItem(
    labelRes: Int,
    value: FeedSort,
    current: FeedSort,
    onClick: (FeedSort) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = { onClick(value) },
        trailingIcon = {
            if (value == current) {
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
 * Fifteen boards do not fit on a 360dp strip, and a bottom sheet was the other candidate.
 *
 * This is the inline version: the strip scrolls, and the button at its end drops a second row in
 * place. It costs vertical space while open, but the finger never leaves the top of the screen and
 * the list underneath stays visible — which a sheet cannot claim.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardStrip(
    boards: List<Board>,
    selectedSlug: String?,
    onBoardClick: (String?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(count = boards.size, key = { boards[it].slug ?: "front" }) { index ->
                    val board = boards[index]
                    BoardPill(
                        board = board,
                        selected = board.slug == selectedSlug,
                        onClick = { onBoardClick(board.slug) },
                    )
                }
            }
            IconButton(onClick = { expanded = !expanded }) {
                Box(
                    modifier =
                    Modifier
                        .size(width = 40.dp, height = 32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription =
                        stringResource(
                            if (expanded) R.string.action_hide_all_boards else R.string.action_show_all_boards,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (expanded) {
            FlowRow(
                modifier =
                Modifier
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                boards.forEach { board ->
                    BoardPill(
                        board = board,
                        selected = board.slug == selectedSlug,
                        onClick = {
                            onBoardClick(board.slug)
                            expanded = false
                        },
                        onSurface = true,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun BoardPill(
    board: Board,
    selected: Boolean,
    onClick: () -> Unit,
    onSurface: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = board.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        },
        // Boards the site refuses to anyone signed out are worth flagging before the tap, not after.
        trailingIcon =
        if (board.adminOnly) {
            {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            null
        },
        shape = CircleShape,
        colors =
        FilterChipDefaults.filterChipColors(
            containerColor =
            if (onSurface) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = null,
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
private fun PostRow(
    post: FeedPost,
    onClick: () -> Unit,
) {
    val summary = post.summary
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (summary.isPinned) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ).padding(start = 14.dp, end = Spacing.lg, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (summary.isPinned) {
            Box(
                modifier =
                Modifier
                    .size(Sizes.avatarList)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = NodeSeekIcons.PushPin,
                    contentDescription = stringResource(R.string.post_badge_pinned),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            UserAvatar(
                url = summary.avatarUrl,
                name = summary.authorName,
                size = Sizes.avatarList,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    // A read thread is dimmed rather than hidden — the list is still the user's
                    // history, and the drop in both weight and contrast is legible at a glance
                    // without adding a badge that would cost a row's worth of space.
                    color =
                    if (post.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (post.isRead) FontWeight.Medium else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (summary.isLocked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.post_badge_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = Spacing.xs)
                            .size(15.dp),
                    )
                }
            }
            PostMetaRow(post)
        }
    }
}

/**
 * The meta line, in the order a scanning eye wants it: what board, who, how busy, how fresh.
 *
 * It flows rather than clips so that a large system font wraps it onto a second line instead of
 * pushing the timestamp — the single most useful item here — off the edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostMetaRow(post: FeedPost) {
    val summary = post.summary
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        BoardTag(title = summary.categoryTitle, slug = summary.categorySlug)
        MetaText(summary.authorName)
        if (summary.isPinned) MetaText(stringResource(R.string.post_badge_pinned))
        if (post.newCommentCount > 0) {
            NewReplyBadge(post.newCommentCount)
        } else {
            summary.commentCount?.let { MetaText(stringResource(R.string.post_reply_count, it)) }
        }
        summary.viewCount?.let { MetaText(stringResource(R.string.post_view_count, it)) }
        summary.lastActiveText?.let { MetaText(it) }
    }
}

/** Replaces the reply count once the user has read the thread: the delta is the useful number. */
@Composable
private fun NewReplyBadge(count: Int) {
    Text(
        text = stringResource(R.string.post_new_reply_count, count),
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(Sizes.avatarList)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SkeletonBar(fraction = width, height = 13.dp)
                    SkeletonBar(fraction = metaWidths[index], height = 11.dp)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SkeletonBar(
    fraction: Float,
    height: Dp,
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

// -------------------------------------------------------------------------------------------------
// Previews use the real scraped rows from the design doc rather than lorem ipsum: the layout has to
// survive a 40-character Chinese title next to a two-character one, and made-up copy never proves it.
// -------------------------------------------------------------------------------------------------

private val previewState =
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

private fun previewFeed(): Flow<PagingData<FeedPost>> =
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
    NodeSeekTheme {
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
    NodeSeekTheme(darkTheme = true) {
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
    NodeSeekTheme { FeedSkeleton() }
}
