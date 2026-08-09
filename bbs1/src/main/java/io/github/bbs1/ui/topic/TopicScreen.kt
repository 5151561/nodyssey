package io.github.bbs1.ui.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.bbs1.R
import io.github.bbs1.net.ApiReply
import io.github.bbs1.net.ApiTopicDetail
import io.github.bbs1.ui.common.apiErrorText
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.richtext.parseMarkdown
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberExternalUriHandler
import io.github.plaza.designsys.richtext.RichContent
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing

private const val LOAD_MORE_LOOKAHEAD = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    state: TopicUiState,
    /** The site the thread lives on; relative links in bodies resolve against it. */
    baseUrl: String,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryAppend: () -> Unit,
    onRefresh: () -> Unit,
) {
    // Custom Tabs speak http(s) only; anything else falls back to whichever app claimed the scheme.
    val uriHandler = rememberExternalUriHandler { it.startsWith("http://") || it.startsWith("https://") }
    // The plugin hands over Markdown as written, so a link can be site-relative the way the web
    // renderer would resolve it. Everything else goes to the browser as-is.
    val openLink: (String) -> Unit = { url ->
        val absolute = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> baseUrl + url
            else -> url
        }
        uriHandler.openUri(absolute)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.topic?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.bbs1_action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()

                state.error != null && state.topic == null ->
                    StatusView(
                        icon = Icons.Default.Warning,
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        iconColor = MaterialTheme.colorScheme.onErrorContainer,
                        title = stringResource(R.string.bbs1_topic_error_title),
                        description = apiErrorText(state.error),
                        primaryAction = StatusAction(stringResource(R.string.bbs1_action_retry), onRefresh),
                    )

                state.topic != null ->
                    TopicContent(state, state.topic, openLink, onLoadMore, onRetryAppend)
            }
        }
    }
}

@Composable
private fun TopicContent(
    state: TopicUiState,
    topic: ApiTopicDetail,
    onLinkClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryAppend: () -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= info.totalItemsCount - LOAD_MORE_LOOKAHEAD
        }
    }
    LaunchedEffect(nearEnd, state.replies.size) {
        if (nearEnd) onLoadMore()
    }
    val now = remember(state.replies) { System.currentTimeMillis() }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "topic") {
            Column(Modifier.padding(horizontal = Spacing.lg)) {
                Text(
                    topic.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.md),
                ) {
                    UserAvatar(
                        url = topic.author.avatar.url.takeIf { it.isNotBlank() },
                        name = topic.author.username.ifBlank { topic.title },
                        size = Sizes.avatarList,
                    )
                    Column {
                        Text(topic.author.username, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            if (topic.forumName.isNotBlank()) MetaText(topic.forumName)
                            if (topic.createdAt > 0) {
                                MetaText(TimeFormat.relative(topic.createdAt * 1000, now))
                            }
                            MetaText(stringResource(R.string.bbs1_meta_views, topic.viewCount))
                        }
                    }
                }
                val body = remember(topic.body) { parseMarkdown(topic.body) }
                RichContent(
                    nodes = body,
                    onLinkClick = onLinkClick,
                    onImageClick = onLinkClick,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
        }

        item(key = "replies-header") {
            Column(Modifier.padding(top = Spacing.xl)) {
                HorizontalDivider()
                Text(
                    text =
                    if (state.replyCount > 0) {
                        stringResource(R.string.bbs1_topic_replies_header, state.replyCount)
                    } else {
                        stringResource(R.string.bbs1_topic_no_replies)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }
        }

        items(state.replies, key = { it.id }) { reply ->
            ReplyItem(reply = reply, nowMillis = now, onLinkClick = onLinkClick)
        }

        when {
            state.appending -> item(key = "append") { AppendSpinner() }

            state.error != null -> item(key = "append-error") {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MetaText(apiErrorText(state.error))
                    TextButton(onClick = onRetryAppend) {
                        Text(stringResource(R.string.bbs1_action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyItem(
    reply: ApiReply,
    nowMillis: Long,
    onLinkClick: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            UserAvatar(
                url = reply.author.avatar.url.takeIf { it.isNotBlank() },
                name = reply.author.username.ifBlank { "?" },
                size = Sizes.avatarList,
            )
            Column {
                Text(reply.author.username, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetaText(stringResource(R.string.bbs1_reply_floor, reply.floor))
                    if (reply.createdAt > 0) {
                        MetaText(TimeFormat.relative(reply.createdAt * 1000, nowMillis))
                    }
                }
            }
        }
        val body = remember(reply.body) { parseMarkdown(reply.body) }
        RichContent(
            nodes = body,
            onLinkClick = onLinkClick,
            onImageClick = onLinkClick,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}
