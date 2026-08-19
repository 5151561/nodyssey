package io.github.nodyssey.ui.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.common.NumericPager
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.postlist.PostRow
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch

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
    AwardScreen(
        state = state,
        onBack = onBack,
        onPostClick = onPostClick,
        onPageSelected = viewModel::load,
        onRetry = viewModel::retry,
        onOpenBrowser = { onOpenBrowser(NodeSeekSite.BASE_URL + NodeSeekSite.awardPath(state.page)) },
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

/**
 * 推荐阅读.
 *
 * The row is the home feed's row, imported rather than reimplemented: this is the same kind of list, and
 * a second implementation would drift — read state, pinned tint, the meta line's order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwardScreen(
    state: AwardUiState,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    onPageSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // A new page starts at its top: keeping the old offset would drop the reader into the middle of
    // content they have not seen.
    LaunchedEffect(state.page) { listState.scrollToItem(0) }

    val appBarState = rememberOneHandAppBarState()
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(R.string.award_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        ) {
            when {
                state.isLoading && state.posts.isEmpty() -> LoadingState(Modifier.fillMaxSize())

                state.error != null && state.posts.isEmpty() ->
                    SiteErrorState(
                        error = state.error,
                        onRetry = onRetry,
                        onOpenBrowser = onOpenBrowser,
                        onSignIn = onSignIn,
                        modifier = Modifier.fillMaxSize(),
                    )

                state.posts.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.award_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else ->
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(count = state.posts.size, key = { state.posts[it].postId }) { index ->
                            val summary = state.posts[index]
                            PostRow(
                                post = FeedPost(summary = summary, isRead = false, newCommentCount = 0),
                                onClick = { onPostClick(summary.postId) },
                                showAwardBadge = false,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
            }
            NumericPager(
                page = state.page,
                totalPages = state.totalPages,
                // The jump above scrolls the list without a gesture, which dispatches no
                // nested scroll, so the app bar has to be told or the new page arrives in whatever
                // is left of the screen below a bar still standing at full height.
                onPageSelected = { page ->
                    scope.launch { appBarState.fold() }
                    onPageSelected(page)
                },
            )
        }
    }
}

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
    PlazaTheme {
        AwardScreen(
            state =
            AwardUiState(
                isLoading = false,
                page = 1,
                totalPages = 18,
                posts =
                listOf(
                    previewSummary(
                        1,
                        "写了个小工具，把 NodeSeek 的帖子同步到 RSS",
                        "nssk",
                        "Dev",
                        "dev",
                        126,
                        8_432,
                        "2天前",
                    ),
                    previewSummary(
                        2,
                        "从零搭一套自用的探针与告警，踩坑全记录",
                        "羽落无声",
                        "技术",
                        "tech",
                        88,
                        6_204,
                        "3天前",
                    ),
                    previewSummary(
                        3,
                        "2026 年低价 VPS 选购避坑指南",
                        "ifreedom",
                        "测评",
                        "review",
                        243,
                        19_051,
                        "上周",
                    ),
                    previewSummary(
                        4,
                        "科普：为什么你的 IPv6 隧道延迟这么高",
                        "jswcph",
                        "技术",
                        "tech",
                        57,
                        4_118,
                        "上周",
                    ),
                ),
            ),
            onBack = {},
            onPostClick = {},
            onPageSelected = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}
