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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.model.PostSummary
import io.github.nsreader.ui.common.ErrorState
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.common.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(
    viewModel: PostListViewModel,
    onPostClick: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val selectedIndex = NodeSeekSite.categories.indexOfFirst { it.slug == state.categorySlug }
        .coerceAtLeast(0)

    // Prefetch one page ahead of the viewport so scrolling never stalls at the seam.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(shouldLoadMore, state.posts.size) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }
    LaunchedEffect(state.categorySlug) {
        listState.scrollToItem(0)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(title = { Text("NodeSeek", fontWeight = FontWeight.Bold) })
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 12.dp,
                    divider = {},
                ) {
                    NodeSeekSite.categories.forEachIndexed { index, category ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectCategory(category.slug) },
                            text = {
                                Text(
                                    text = category.title,
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
                state.posts.isEmpty() && state.isLoading -> LoadingState()

                state.posts.isEmpty() && state.error != null -> ErrorState(
                    message = state.error!!,
                    challenge = state.challenge,
                    onRetry = viewModel::refresh,
                    onOpenBrowser = { onOpenBrowser(viewModel.challengeUrl()) },
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = viewModel::refresh,
                ) {
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
                post.commentCount?.let { MetaText("$it 回复") }
                post.viewCount?.let { MetaText("$it 浏览") }
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
