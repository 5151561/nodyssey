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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.data.Board
import io.github.nsreader.model.PostSummary
import io.github.nsreader.ui.common.ErrorState
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.theme.NodeSeekTheme

/**
 * Stateful entry point. It only wires the ViewModel to the stateless [PostListScreen] below, which
 * is what keeps the screen previewable and testable without a running app.
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
        onPostClick = onPostClick,
        onBoardClick = viewModel::selectCategory,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadNextPage,
        onSignInClick = { onOpenBrowser(NodeSeekSite.BASE_URL + NodeSeekSite.SIGN_IN_PATH) },
        onRecoverInBrowser = { onOpenBrowser(viewModel.challengeUrl()) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(
    state: PostListUiState,
    onPostClick: (Long) -> Unit,
    onBoardClick: (String?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSignInClick: () -> Unit,
    onRecoverInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Prefetch one page ahead of the viewport so scrolling never stalls at the seam.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(shouldLoadMore, state.posts.size) {
        if (shouldLoadMore) onLoadMore()
    }
    LaunchedEffect(state.categorySlug) { listState.scrollToItem(0) }

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
            val error = state.error
            when {
                state.posts.isEmpty() && state.isLoading -> LoadingState()

                state.posts.isEmpty() && error != null -> ErrorState(
                    error = error,
                    onRetry = onRefresh,
                    onOpenBrowser = onRecoverInBrowser,
                )

                else -> PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.posts, key = { it.postId }) { post ->
                            PostRow(post = post, onClick = { onPostClick(post.postId) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (state.isAppending) {
                            item {
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

@Composable
private fun PostRow(post: PostSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserAvatar(url = post.avatarUrl, name = post.authorName, size = 34.dp)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                post.categoryTitle?.let { CategoryChip(it) }
                MetaText(post.authorName)
                post.commentCount?.let { MetaText(stringResource(R.string.post_reply_count, it)) }
                post.viewCount?.let { MetaText(stringResource(R.string.post_view_count, it)) }
                post.lastActiveText?.let { MetaText(it) }
            }
        }
    }
}

@Composable
private fun CategoryChip(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
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

private val previewState = PostListUiState(
    boards = listOf(
        Board(null, "综合", null),
        Board("daily", "日常", null),
        Board("tech", "技术", null),
        Board("trade", "交易", null),
    ),
    posts = listOf(
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
    ),
)

@Preview(showBackground = true, name = "Post list")
@Composable
private fun PostListScreenPreview() {
    NodeSeekTheme {
        PostListScreen(
            state = previewState,
            onPostClick = {},
            onBoardClick = {},
            onRefresh = {},
            onLoadMore = {},
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
            onPostClick = {},
            onBoardClick = {},
            onRefresh = {},
            onLoadMore = {},
            onSignInClick = {},
            onRecoverInBrowser = {},
        )
    }
}
