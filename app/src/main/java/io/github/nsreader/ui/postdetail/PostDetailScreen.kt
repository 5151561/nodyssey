package io.github.nsreader.ui.postdetail

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import io.github.nsreader.model.PostContent
import io.github.nsreader.ui.common.ErrorState
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.richtext.RichContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    viewModel: PostDetailViewModel,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.comments.size) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenBrowser(viewModel.postUrl()) }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "在网页中打开")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                state.body == null && state.isLoading -> LoadingState()

                state.body == null && state.error != null -> ErrorState(
                    message = state.error!!,
                    challenge = state.challenge,
                    onRetry = viewModel::refresh,
                    onOpenBrowser = { onOpenBrowser(viewModel.postUrl()) },
                )

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "title") {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                    state.body?.let { body ->
                        item(key = "body") {
                            ContentBlock(
                                content = body,
                                onOpenBrowser = onOpenBrowser,
                                onImageClick = onImageClick,
                            )
                            HorizontalDivider(
                                thickness = 6.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                    items(state.comments, key = { it.commentId ?: it.hashCode().toLong() }) { comment ->
                        ContentBlock(
                            content = comment,
                            onOpenBrowser = onOpenBrowser,
                            onImageClick = onImageClick,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (state.isAppending) {
                        item(key = "appending") {
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

@Composable
private fun ContentBlock(
    content: PostContent,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserAvatar(url = content.avatarUrl, name = content.authorName, size = 28.dp)
            Text(
                text = content.authorName,
                style = MaterialTheme.typography.titleSmall,
            )
            if (content.isOriginalPoster) {
                Text(
                    text = "楼主",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Box(Modifier.weight(1f))
            content.floor?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content.createdAtText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp, top = 2.dp),
            )
        }
        RichContent(
            nodes = content.nodes,
            onLinkClick = onOpenBrowser,
            onImageClick = onImageClick,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
