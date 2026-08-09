package io.github.bbs1.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.github.bbs1.R
import io.github.bbs1.net.ApiTopicSummary
import io.github.bbs1.ui.common.apiErrorText
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.AvatarCapOffset
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView
import io.github.plaza.designsys.component.ThreadRow
import io.github.plaza.designsys.component.ThreadRowTitle
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing

/** How close to the end the reader gets before the next page is asked for. */
private const val LOAD_MORE_LOOKAHEAD = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenInstances: () -> Unit,
    onOpenTopic: (Long) -> Unit,
    onSelectForum: (Long?) -> Unit,
    onLoadMore: () -> Unit,
    onRetryAppend: () -> Unit,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCompose: () -> Unit,
) {
    var accountOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.instance?.name ?: stringResource(R.string.bbs1_app_name)) },
                actions = {
                    if (state.instance != null) {
                        val session = state.session
                        IconButton(onClick = { if (session == null) onSignIn() else accountOpen = true }) {
                            if (session == null) {
                                Icon(
                                    PlazaIcons.Login,
                                    contentDescription = stringResource(R.string.bbs1_login_title),
                                )
                            } else {
                                UserAvatar(
                                    url = session.avatarUrl.takeIf { it.isNotBlank() },
                                    name = session.username,
                                    size = Sizes.avatarComment,
                                )
                            }
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.bbs1_action_refresh),
                            )
                        }
                    }
                    IconButton(onClick = onOpenInstances) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.bbs1_manage_instances),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canPost) {
                FloatingActionButton(onClick = onCompose) {
                    Icon(
                        PlazaIcons.AddComment,
                        contentDescription = stringResource(R.string.bbs1_compose_topic_title),
                    )
                }
            }
        },
    ) { padding ->
        val session = state.session
        if (accountOpen && session != null) {
            AccountSheet(
                session = session,
                siteName = state.instance?.name.orEmpty(),
                onDismiss = { accountOpen = false },
                onSignOut = {
                    accountOpen = false
                    onSignOut()
                },
            )
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.instance == null ->
                    StatusView(
                        icon = Icons.Default.Info,
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.bbs1_home_no_instance_title),
                        description = stringResource(R.string.bbs1_home_no_instance_body),
                        primaryAction = StatusAction(stringResource(R.string.bbs1_manage_instances), onOpenInstances),
                    )

                state.loading && state.topics.isEmpty() -> LoadingState()

                state.error != null && state.topics.isEmpty() ->
                    StatusView(
                        icon = Icons.Default.Warning,
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        iconColor = MaterialTheme.colorScheme.onErrorContainer,
                        title = stringResource(R.string.bbs1_home_error_title),
                        description = apiErrorText(state.error),
                        primaryAction = StatusAction(stringResource(R.string.bbs1_action_retry), onRefresh),
                    )

                else -> TopicList(state, onOpenTopic, onSelectForum, onLoadMore, onRetryAppend)
            }
        }
    }
}

@Composable
private fun TopicList(
    state: HomeUiState,
    onOpenTopic: (Long) -> Unit,
    onSelectForum: (Long?) -> Unit,
    onLoadMore: () -> Unit,
    onRetryAppend: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (state.forums.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = state.selectedForumId == null,
                        onClick = { onSelectForum(null) },
                        label = { Text(stringResource(R.string.bbs1_forum_all)) },
                    )
                }
                items(state.forums, key = { it.id }) { forum ->
                    FilterChip(
                        selected = state.selectedForumId == forum.id,
                        onClick = { onSelectForum(forum.id) },
                        label = { Text(forum.name) },
                    )
                }
            }
        }

        val listState = rememberLazyListState()
        // Derived so scrolling recomposes nothing until the answer flips, and keyed on the trigger
        // in LaunchedEffect so a still list near the end still asks again after an append lands.
        val nearEnd by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                last.index >= info.totalItemsCount - LOAD_MORE_LOOKAHEAD
            }
        }
        LaunchedEffect(nearEnd, state.topics.size) {
            if (nearEnd) onLoadMore()
        }
        // One clock per loaded page, not per frame: relative stamps only need to move when content does.
        val now = remember(state.topics) { System.currentTimeMillis() }

        if (state.topics.isEmpty() && !state.loading) {
            StatusView(
                icon = Icons.Default.Info,
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                title = stringResource(R.string.bbs1_home_empty_title),
                description = stringResource(R.string.bbs1_home_empty_body),
            )
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(state.topics, key = { it.id }) { topic ->
                TopicRow(topic = topic, nowMillis = now, onClick = { onOpenTopic(topic.id) })
            }
            when {
                state.appending -> item(key = "append") { AppendSpinner() }

                state.error != null -> item(key = "append-error") {
                    Column(
                        Modifier.padding(Spacing.lg),
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
}

@Composable
private fun TopicRow(
    topic: ApiTopicSummary,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    ThreadRow(
        onClick = onClick,
        leading = {
            UserAvatar(
                url = topic.author.avatar.url.takeIf { it.isNotBlank() },
                name = topic.author.username.ifBlank { topic.title },
                size = Sizes.avatarList,
                modifier = Modifier.offset(y = AvatarCapOffset),
            )
        },
        title = {
            if (topic.isPinned == 1) {
                TonalTag(
                    text = stringResource(R.string.bbs1_topic_pinned),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
            }
            ThreadRowTitle(text = AnnotatedString(topic.title))
        },
        meta = {
            if (topic.forumName.isNotBlank()) MetaText(topic.forumName)
            MetaText(topic.author.username)
            MetaText(stringResource(R.string.bbs1_meta_replies, topic.replyCount))
            // The forum sorts by last activity; show the stamp that ordering is about. A topic with
            // no replies has last_reply_at = 0 and falls back to its creation time.
            val stampSeconds = if (topic.lastReplyAt > 0) topic.lastReplyAt else topic.createdAt
            if (stampSeconds > 0) {
                MetaText(TimeFormat.relative(stampSeconds * 1000, nowMillis))
            }
        },
    )
}
