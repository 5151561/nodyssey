package io.github.nsreader.ui.postlist

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.data.Board
import io.github.nsreader.data.FeedPost
import io.github.nsreader.model.PostSummary
import io.github.nsreader.ui.common.ErrorState
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.theme.NodeSeekTheme
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
    onSignInClick: () -> Unit,
    onRecoverInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Switching boards is the one case where the previous scroll offset is meaningless.
    LaunchedEffect(state.categorySlug) { listState.scrollToItem(0) }

    val refreshState = posts.loadState.refresh
    val appendState = posts.loadState.append

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onSignInClick) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = stringResource(R.string.action_sign_in),
                            )
                        }
                    },
                )
                PrimaryScrollableTabRow(
                    selectedTabIndex = state.selectedBoardIndex,
                    edgePadding = 12.dp,
                    divider = {},
                ) {
                    state.boards.forEachIndexed { index, board ->
                        Tab(
                            selected = index == state.selectedBoardIndex,
                            onClick = { onBoardClick(board.slug) },
                            text = {
                                Text(
                                    text = board.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                posts.itemCount == 0 && refreshState is LoadState.Loading -> {
                    LoadingState()
                }

                // An error only takes over the screen when there is nothing cached to show. With rows
                // on screen the failure is not worth losing the content over.
                posts.itemCount == 0 && refreshState is LoadState.Error -> {
                    ErrorState(
                        error = refreshState.error.toNodeSeekError(),
                        onRetry = posts::refresh,
                        onOpenBrowser = onRecoverInBrowser,
                    )
                }

                else -> {
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
                                    PostRow(post = post, onClick = { onPostClick(post.summary.postId) })
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                            if (appendState is LoadState.Loading) {
                                item(key = "append-spinner") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
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
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserAvatar(url = summary.avatarUrl, name = summary.authorName, size = 34.dp)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                // A read thread is dimmed rather than hidden — the list is still the user's history.
                color =
                if (post.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                summary.categoryTitle?.let { CategoryChip(it) }
                MetaText(summary.authorName)
                if (post.newCommentCount > 0) {
                    NewReplyBadge(post.newCommentCount)
                } else {
                    summary.commentCount?.let {
                        MetaText(stringResource(R.string.post_reply_count, it))
                    }
                }
                summary.viewCount?.let { MetaText(stringResource(R.string.post_view_count, it)) }
                summary.lastActiveText?.let { MetaText(it) }
            }
        }
    }
}

/** Replaces the reply count once the user has read the thread: the delta is the useful number. */
@Composable
private fun NewReplyBadge(count: Int) {
    Text(
        text = stringResource(R.string.post_new_reply_count, count),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun CategoryChip(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val previewState =
    PostListUiState(
        boards =
        listOf(
            Board(null, "综合", null),
            Board("daily", "日常", null),
            Board("tech", "技术", null),
            Board("trade", "交易", null),
        ),
    )

private fun previewFeed(): Flow<PagingData<FeedPost>> =
    flowOf(
        PagingData.from(
            listOf(
                FeedPost(
                    summary =
                    PostSummary(
                        postId = 1,
                        title = "iLatency公测，一个新的社区内Ping站，邀你共建",
                        authorName = "酒神",
                        authorUid = 1,
                        avatarUrl = null,
                        categoryTitle = "Dev",
                        categorySlug = "dev",
                        viewCount = 30594,
                        commentCount = 340,
                        lastActiveText = "28分钟前",
                        lastActiveTitle = null,
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                // Read, with replies since — the state the read-mark table exists to render.
                FeedPost(
                    summary =
                    PostSummary(
                        postId = 2,
                        title = "移动说回馈老用户，十一年宽带合约，一次性交3年宽带费，后续8年不缴费，还送一部手机",
                        authorName = "宝宝困困",
                        authorUid = 2,
                        avatarUrl = null,
                        categoryTitle = "日常",
                        categorySlug = "daily",
                        viewCount = 1551,
                        commentCount = 53,
                        lastActiveText = "11秒前",
                        lastActiveTitle = null,
                    ),
                    isRead = true,
                    newCommentCount = 4,
                ),
                FeedPost(
                    summary =
                    PostSummary(
                        postId = 3,
                        title = "收北京腾讯云无忧235",
                        authorName = "jswcph",
                        authorUid = 3,
                        avatarUrl = null,
                        categoryTitle = "交易",
                        categorySlug = "trade",
                        viewCount = 2,
                        commentCount = 0,
                        lastActiveText = "3秒前",
                        lastActiveTitle = null,
                    ),
                    isRead = true,
                    newCommentCount = 0,
                ),
            ),
        ),
    )

@Preview(showBackground = true, name = "Post list")
@Composable
private fun PostListScreenPreview() {
    NodeSeekTheme {
        PostListScreen(
            state = previewState,
            posts = previewFeed().collectAsLazyPagingItems(),
            onPostClick = {},
            onBoardClick = {},
            onSignInClick = {},
            onRecoverInBrowser = {},
        )
    }
}

@Preview(showBackground = true, name = "Post list · dark")
@Composable
private fun PostListScreenDarkPreview() {
    NodeSeekTheme(darkTheme = true) {
        PostListScreen(
            state = previewState,
            posts = previewFeed().collectAsLazyPagingItems(),
            onPostClick = {},
            onBoardClick = {},
            onSignInClick = {},
            onRecoverInBrowser = {},
        )
    }
}
