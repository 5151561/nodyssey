package io.github.nodyssey.ui.space

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.LoadingState
import io.github.nodyssey.ui.common.NodeSeekErrorState
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.composer.collapseMarkdown
import io.github.nodyssey.ui.composer.parseMarkdown
import io.github.nodyssey.ui.postlist.toNodeSeekError
import io.github.nodyssey.ui.richtext.RichContent
import io.github.plaza.designsys.component.NodysseyIcons
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.NodysseyTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.flowOf

@Composable
fun UserSpaceRoute(
    viewModel: UserSpaceViewModel,
    onBack: () -> Unit,
    onPostClick: (Long, String?) -> Unit,
    onMessage: (Long) -> Unit,
    onEditProfile: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** Readme/bio links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topics =
        if (state.selectedTab == SpaceTab.TOPICS) viewModel.topics.collectAsLazyPagingItems() else null
    val comments =
        if (state.selectedTab == SpaceTab.COMMENTS) viewModel.comments.collectAsLazyPagingItems() else null
    val collections =
        if (state.selectedTab == SpaceTab.COLLECTIONS) viewModel.collections.collectAsLazyPagingItems() else null
    UserSpaceScreen(
        state = state,
        topics = topics,
        comments = comments,
        collections = collections,
        onBack = onBack,
        onTabSelected = viewModel::selectTab,
        onPostClick = onPostClick,
        onRetryProfile = viewModel::refreshProfile,
        onMessage = { onMessage(state.uid) },
        onToggleFollow = viewModel::toggleFollow,
        onFollowFailureShown = viewModel::onFollowFailureShown,
        onEditProfile = onEditProfile,
        onOpenBrowser = onOpenBrowser,
        onLinkClick = onLinkClick,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSpaceScreen(
    state: UserSpaceUiState,
    onBack: () -> Unit,
    onTabSelected: (SpaceTab) -> Unit,
    onPostClick: (Long, String?) -> Unit,
    onRetryProfile: () -> Unit,
    onMessage: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFollow: () -> Unit = {},
    onFollowFailureShown: () -> Unit = {},
    topics: LazyPagingItems<SpacePost>? = null,
    comments: LazyPagingItems<SpaceComment>? = null,
    collections: LazyPagingItems<SpacePost>? = null,
    /** Readme/bio links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
) {
    val spaceUrl = NodeSeekSite.BASE_URL + NodeSeekSite.spacePath(state.uid)
    val snackbarHostState = remember { SnackbarHostState() }

    FollowFailureEffect(
        failure = state.followFailure,
        snackbarHostState = snackbarHostState,
        onSignIn = onSignIn,
        onShown = onFollowFailureShown,
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.isSelf) {
                        IconButton(onClick = onEditProfile) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.profile_edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    SpaceOverflowMenu(onOpenBrowser = { onOpenBrowser(spaceUrl) })
                },
            )
        },
    ) { padding ->
        if (state.isLoadingProfile && !state.hasProfile) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (state.error != null && !state.hasProfile) {
            NodeSeekErrorState(
                error = state.error,
                onRetry = onRetryProfile,
                onOpenBrowser = { onOpenBrowser(spaceUrl) },
                onSignIn = onSignIn,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        ) {
            SpaceHeader(state = state, onMessage = onMessage, onToggleFollow = onToggleFollow)
            SpaceStatsRow(state)
            PrimaryTabRow(selectedTabIndex = state.tabs.indexOf(state.selectedTab).coerceAtLeast(0)) {
                state.tabs.forEach { tab ->
                    Tab(
                        selected = tab == state.selectedTab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(stringResource(tab.labelRes())) },
                    )
                }
            }
            SpaceTabContent(
                state = state,
                topics = topics,
                comments = comments,
                collections = collections,
                onPostClick = onPostClick,
                onOpenBrowser = onOpenBrowser,
                onLinkClick = onLinkClick,
                onSignIn = onSignIn,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SpaceOverflowMenu(onOpenBrowser: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                NodysseyIcons.OpenInNew,
                contentDescription = stringResource(R.string.action_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_in_browser)) },
                onClick = {
                    open = false
                    onOpenBrowser()
                },
            )
        }
    }
}

/**
 * A refused follow, said once.
 *
 * The site's own sentence wins when there is one — "对方已屏蔽你" explains itself — and our wording only
 * stands in for the failures that never reached the site. Being signed out is the exception that gets an
 * action instead of a sentence, because it is the one failure the reader can clear from here.
 */
@Composable
private fun FollowFailureEffect(
    failure: FollowFailure?,
    snackbarHostState: SnackbarHostState,
    onSignIn: () -> Unit,
    onShown: () -> Unit,
) {
    val fallback = failure?.error?.shortMessage()
    val signInLabel = stringResource(R.string.action_sign_in)
    LaunchedEffect(failure) {
        if (failure == null) return@LaunchedEffect
        val needsSignIn = failure.error == NodeSeekError.LoginRequired
        val result =
            snackbarHostState.showSnackbar(
                message = failure.detail?.takeIf { it.isNotBlank() } ?: fallback.orEmpty(),
                actionLabel = signInLabel.takeIf { needsSignIn },
            )
        if (result == SnackbarResult.ActionPerformed) onSignIn()
        onShown()
    }
}

@Composable
private fun SpaceHeader(
    state: UserSpaceUiState,
    onMessage: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UserAvatar(url = state.avatarUrl, name = state.name, size = 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    state.level?.let { level ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.assets_level, level),
                                style =
                                MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(
                    text =
                    state.bio
                        ?.let { stringResource(R.string.space_uid_bio, state.uid, it) }
                        ?: stringResource(R.string.space_uid, state.uid),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The two actions the site offers on someone else's space, in the site's own order.
        if (!state.isSelf) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.canFollow) {
                    FollowButton(
                        followed = state.followed == true,
                        onClick = onToggleFollow,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text(
                        stringResource(R.string.space_message),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * 关注 / 已关注, as one button that says what pressing it will do next.
 *
 * Filled while unfollowed and outlined once followed, which is the same demotion the site performs by
 * turning its blue button grey: after the relationship exists, undoing it is not the action this screen
 * is for. The label is the *current* state rather than the pending action — "已关注" beside an outlined
 * button reads as a toggle that is on, where "取关" would read as a warning.
 */
@Composable
private fun FollowButton(
    followed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = if (followed) Icons.Default.Check else Icons.Default.Add
    val label = stringResource(if (followed) R.string.space_following else R.string.space_follow)
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
    val buttonModifier = modifier.height(44.dp)

    if (followed) {
        OutlinedButton(onClick = onClick, modifier = buttonModifier, content = content)
    } else {
        Button(onClick = onClick, modifier = buttonModifier, content = content)
    }
}

/**
 * The five statistics the site actually publishes for an account.
 *
 * Not four, not seven: 加入天数 / 等级 / 鸡腿 / 主题帖 / 评论. `getInfo` does also carry `fans` and
 * `follows`, but a sixth and seventh column would squeeze all of them below the width where the labels
 * stay readable, and the two lists they count already have a screen of their own (8c 关注与粉丝).
 */
@Composable
private fun SpaceStatsRow(state: UserSpaceUiState) {
    Row(Modifier.padding(horizontal = 10.dp)) {
        SpaceStat(state.joinedDays?.toString(), stringResource(R.string.space_stat_joined_days))
        SpaceStat(
            state.level?.let { stringResource(R.string.assets_level, it) },
            stringResource(R.string.space_stat_level),
        )
        SpaceStat(state.chickenCount?.formatted(), stringResource(R.string.space_stat_chicken))
        SpaceStat(state.topicCount?.formatted(), stringResource(R.string.space_stat_topics))
        SpaceStat(state.commentCount?.formatted(), stringResource(R.string.space_stat_comments))
    }
}

@Composable
private fun RowScope.SpaceStat(
    value: String?,
    label: String,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value ?: UNKNOWN_VALUE,
            style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val UNKNOWN_VALUE = "—"

private fun Int.formatted(): String = if (this >= 1_000) "%,d".format(this) else toString()

@Composable
private fun SpaceTabContent(
    state: UserSpaceUiState,
    topics: LazyPagingItems<SpacePost>?,
    comments: LazyPagingItems<SpaceComment>?,
    collections: LazyPagingItems<SpacePost>?,
    onPostClick: (Long, String?) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.selectedTab) {
        SpaceTab.GENERAL -> GeneralTab(state, onLinkClick, modifier)

        SpaceTab.TOPICS ->
            SpaceListTab(
                list = topics,
                emptyText = stringResource(R.string.space_empty_topics),
                endTextRes = R.string.space_end_topics,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                key = { _, post -> post.postId },
                modifier = modifier,
            ) { post ->
                SpacePostRow(post = post, onClick = { onPostClick(post.postId, null) })
            }

        SpaceTab.COMMENTS ->
            SpaceListTab(
                list = comments,
                emptyText = stringResource(R.string.space_empty_comments),
                endTextRes = R.string.space_end_comments,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                // The payload's own id when it has one; the position only for the rare row without.
                key = { index, comment -> comment.commentId ?: "comment-$index" },
                modifier = modifier,
            ) { comment ->
                // The floor rides along so the thread opens at this very comment.
                SpaceCommentRow(
                    comment = comment,
                    onClick = { onPostClick(comment.postId, comment.floor) },
                )
            }

        SpaceTab.COLLECTIONS ->
            SpaceListTab(
                list = collections,
                emptyText = stringResource(R.string.space_empty_collections),
                endTextRes = R.string.space_end_collections,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                key = { _, post -> post.postId },
                modifier = modifier,
            ) { post ->
                SpacePostRow(post = post, onClick = { onPostClick(post.postId, null) })
            }
    }
}

/** 概况: one sentence of Bio, then the Readme the user wrote in Markdown. */
@Composable
private fun GeneralTab(
    state: UserSpaceUiState,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var readmeExpanded by rememberSaveable(state.uid) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        state.bio?.let { bio ->
            SpaceCard(title = stringResource(R.string.space_bio)) {
                Text(bio, style = MaterialTheme.typography.bodyMedium)
            }
        }
        SpaceCard(title = stringResource(R.string.space_readme)) {
            val readme = state.readme?.takeIf { it.isNotBlank() }
            if (readme == null) {
                // The site's own empty state, emoji included. Writing our own would have been a
                // worse sentence and a less familiar one.
                Text(
                    stringResource(R.string.space_readme_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val nodes = remember(readme, readmeExpanded) {
                    val markdown =
                        if (readmeExpanded) readme else collapseMarkdown(readme, README_COLLAPSED_LINES)
                    parseMarkdown(markdown)
                }
                RichContent(
                    nodes = nodes,
                    onLinkClick = onOpenBrowser,
                    onImageClick = onOpenBrowser,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                if (readme.lines().size > README_COLLAPSED_LINES) {
                    TextButton(onClick = { readmeExpanded = !readmeExpanded }) {
                        Text(
                            stringResource(
                                if (readmeExpanded) {
                                    R.string.space_readme_collapse
                                } else {
                                    R.string.space_readme_expand
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

private const val README_COLLAPSED_LINES = 8

@Composable
private fun SpaceCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

/**
 * The list tabs, which differ only in their row and their end-of-list sentence.
 *
 * The repository remains page-numbered, while Paging 3 owns loading, retries and append state.
 */
@Composable
private fun <T : Any> SpaceListTab(
    list: LazyPagingItems<T>?,
    emptyText: String,
    endTextRes: Int,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    /** Stable row identity, so a page-1 replace recomposes only the rows that actually changed. */
    key: (index: Int, item: T) -> Any,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    if (list == null) {
        LoadingState(modifier)
        return
    }
    if (list.itemCount == 0) {
        when (val refresh = list.loadState.refresh) {
            LoadState.Loading -> LoadingState(modifier)

            is LoadState.Error ->
                NodeSeekErrorState(
                    error = refresh.error.toNodeSeekError(),
                    onRetry = list::retry,
                    onOpenBrowser = { onOpenBrowser(NodeSeekSite.BASE_URL) },
                    onSignIn = onSignIn,
                    modifier = modifier,
                )

            is LoadState.NotLoading ->
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 72.dp),
                    )
                }
        }
        return
    }

    LazyColumn(modifier) {
        items(
            count = list.itemCount,
            key = { index -> list.peek(index)?.let { key(index, it) } ?: index },
        ) { index ->
            list[index]?.let { item ->
                row(item)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item(key = "footer") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (list.loadState.append) {
                    LoadState.Loading -> CircularProgressIndicator(Modifier.size(22.dp))

                    is LoadState.Error ->
                        TextButton(onClick = list::retry) {
                            Text(stringResource(R.string.action_retry))
                        }

                    is LoadState.NotLoading ->
                        if (list.loadState.append.endOfPaginationReached) {
                            Text(
                                text = stringResource(endTextRes, list.itemCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            CircularProgressIndicator(Modifier.size(22.dp))
                        }
                }
            }
        }
    }
}

@Composable
private fun SpacePostRow(
    post: SpacePost,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            post.categoryTitle?.let { BoardTag(title = it, slug = post.categorySlug) }
            post.authorName?.let { RowMeta(it) }
            post.commentCount?.let { RowMeta(stringResource(R.string.post_reply_count, it)) }
            post.viewCount?.let { RowMeta(stringResource(R.string.post_view_count, it)) }
            post.createdAtText?.let { RowMeta(it) }
        }
    }
}

@Composable
private fun SpaceCommentRow(
    comment: SpaceComment,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = comment.excerpt,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            comment.postTitle?.let {
                RowMeta(stringResource(R.string.space_comment_in, it), weighted = true)
            }
            comment.createdAtText?.let { RowMeta(it) }
        }
    }
}

@Composable
private fun RowMeta(
    text: String,
    weighted: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (weighted) Modifier.fillMaxWidth(0.7f) else Modifier,
    )
}

private fun SpaceTab.labelRes(): Int =
    when (this) {
        SpaceTab.GENERAL -> R.string.space_tab_general
        SpaceTab.TOPICS -> R.string.space_tab_topics
        SpaceTab.COMMENTS -> R.string.space_tab_comments
        SpaceTab.COLLECTIONS -> R.string.space_tab_collections
    }

// -------------------------------------------------------------------------------------------------

private val previewTopics =
    listOf(
        SpacePost(1, "第一次用 nftables 做端口转发，记录一下踩的坑", "技术", "tech", null, 12, 843, "3天前"),
        SpacePost(2, "签到鸡腿是随机的吗？连续七天都是 6 个", "日常", "daily", null, 18, 976, "上周"),
        SpacePost(3, "求推荐一台香港小鸡，跑 uptime 监控用", "日常", "daily", null, 21, 1024, "6月11日"),
        SpacePost(4, "【出】甲骨文 ARM 一台求接手", "交易", "trade", null, 6, 412, "5月2日"),
        SpacePost(5, "Debian 12 升 13 之后 ss 命令输出格式变了？", "技术", "tech", null, 9, 655, "4月20日"),
    )

private val previewSelfState =
    UserSpaceUiState(
        uid = 12043,
        isSelf = true,
        isLoadingProfile = false,
        name = "花田错不错",
        level = 1,
        bio = "用一句话介绍自己",
        joinedDays = 143,
        chickenCount = 344,
        topicCount = 5,
        commentCount = 96,
        selectedTab = SpaceTab.TOPICS,
    )

private val previewPublicState =
    UserSpaceUiState(
        uid = 4471,
        isSelf = false,
        isLoadingProfile = false,
        name = "nssk",
        level = 4,
        bio = "写点脚本，养几只小鸡。",
        readme =
        """
        ## 关于我
        常年折腾 Debian 与低延迟线路，偶尔写测评。

        - 交易只走论坛担保，不接私下转账
        - 博客：[blog.nssk.dev](https://blog.nssk.dev)
        - 探针：[ping.nssk.dev](https://ping.nssk.dev)
        """.trimIndent(),
        joinedDays = 745,
        chickenCount = 2041,
        topicCount = 128,
        commentCount = 1904,
        followed = false,
        selectedTab = SpaceTab.GENERAL,
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8a 我的主页 · 主题帖")
@Composable
private fun UserSpaceSelfPreview() {
    NodysseyTheme { PreviewScreen(previewSelfState) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8b 公开用户页 · 概况")
@Composable
private fun UserSpacePublicPreview() {
    NodysseyTheme { PreviewScreen(previewPublicState) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8a 我的主页 · dark")
@Composable
private fun UserSpaceSelfDarkPreview() {
    NodysseyTheme(darkTheme = true) { PreviewScreen(previewSelfState) }
}

@Composable
private fun PreviewScreen(state: UserSpaceUiState) {
    val topics = flowOf(PagingData.from(previewTopics)).collectAsLazyPagingItems()
    UserSpaceScreen(
        state = state,
        topics = topics,
        onBack = {},
        onTabSelected = {},
        onPostClick = { _, _ -> },
        onRetryProfile = {},
        onMessage = {},
        onEditProfile = {},
        onOpenBrowser = {},
        onSignIn = {},
    )
}
