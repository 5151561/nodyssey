package io.github.nsreader.ui.space

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.SpaceComment
import io.github.nsreader.data.SpacePost
import io.github.nsreader.ui.common.BoardTag
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.composer.parseMarkdown
import io.github.nsreader.ui.richtext.RichContent
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.TABULAR_FIGURES
import io.github.nsreader.ui.theme.readableWidth

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
    UserSpaceScreen(
        state = state,
        onBack = onBack,
        onTabSelected = viewModel::selectTab,
        onPostClick = onPostClick,
        onLoadMore = viewModel::loadMore,
        onRetryTab = viewModel::retryTab,
        onRetryProfile = viewModel::refreshProfile,
        onMessage = { onMessage(state.uid) },
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
    onLoadMore: (SpaceTab) -> Unit,
    onRetryTab: (SpaceTab) -> Unit,
    onRetryProfile: () -> Unit,
    onMessage: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** Readme/bio links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
) {
    val spaceUrl = NodeSeekSite.BASE_URL + NodeSeekSite.spacePath(state.uid)

    Scaffold(
        modifier = modifier,
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
                                NodeSeekIcons.Edit,
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
            SpaceHeader(state = state, onMessage = onMessage)
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
                onPostClick = onPostClick,
                onLoadMore = onLoadMore,
                onRetryTab = onRetryTab,
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
                NodeSeekIcons.OpenInNew,
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

@Composable
private fun SpaceHeader(
    state: UserSpaceUiState,
    onMessage: () -> Unit,
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
        // The site's only action on someone else's space. There is no follow button to add: the
        // relationship endpoints exist but nothing on the site exposes them.
        if (!state.isSelf) {
            OutlinedButton(
                onClick = onMessage,
                modifier = Modifier
                    .fillMaxWidth()
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

/**
 * The five statistics the site actually publishes for an account.
 *
 * Not four, not seven: 加入天数 / 等级 / 鸡腿 / 主题帖 / 评论. Follower and like counts were on an
 * earlier draft of this screen and do not exist anywhere on NodeSeek, so a placeholder for them would
 * have been a number we invented.
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
    onPostClick: (Long, String?) -> Unit,
    onLoadMore: (SpaceTab) -> Unit,
    onRetryTab: (SpaceTab) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.selectedTab) {
        SpaceTab.GENERAL -> GeneralTab(state, onLinkClick, modifier)

        SpaceTab.TOPICS ->
            SpaceListTab(
                list = state.topics,
                tab = SpaceTab.TOPICS,
                emptyText = stringResource(R.string.space_empty_topics),
                endTextRes = R.string.space_end_topics,
                onLoadMore = onLoadMore,
                onRetryTab = onRetryTab,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                key = { _, post -> post.postId },
                modifier = modifier,
            ) { post ->
                SpacePostRow(post = post, onClick = { onPostClick(post.postId, null) })
            }

        SpaceTab.COMMENTS ->
            SpaceListTab(
                list = state.comments,
                tab = SpaceTab.COMMENTS,
                emptyText = stringResource(R.string.space_empty_comments),
                endTextRes = R.string.space_end_comments,
                onLoadMore = onLoadMore,
                onRetryTab = onRetryTab,
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
                list = state.collections,
                tab = SpaceTab.COLLECTIONS,
                emptyText = stringResource(R.string.space_empty_collections),
                endTextRes = R.string.space_end_collections,
                onLoadMore = onLoadMore,
                onRetryTab = onRetryTab,
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
                        if (readmeExpanded) readme else readme.lines().take(README_COLLAPSED_LINES).joinToString("\n")
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
 * Paging is by "load more" rather than by Paging 3: these lists are short (five topics, ninety-six
 * comments) and the endpoints are page-numbered XHRs, so a RemoteMediator would add a Room table and a
 * cache-invalidation question to save nothing.
 */
@Composable
private fun <T> SpaceListTab(
    list: SpaceListState<T>,
    tab: SpaceTab,
    emptyText: String,
    endTextRes: Int,
    onLoadMore: (SpaceTab) -> Unit,
    onRetryTab: (SpaceTab) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    /** Stable row identity, so a page-1 replace recomposes only the rows that actually changed. */
    key: (index: Int, item: T) -> Any,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    if (list.items.isEmpty()) {
        when {
            list.isLoading -> LoadingState(modifier)

            list.error != null ->
                NodeSeekErrorState(
                    error = list.error,
                    onRetry = { onRetryTab(tab) },
                    onOpenBrowser = { onOpenBrowser(NodeSeekSite.BASE_URL) },
                    onSignIn = onSignIn,
                    modifier = modifier,
                )

            else ->
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
        items(count = list.items.size, key = { key(it, list.items[it]) }) { index ->
            row(list.items[index])
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        listFooter(list = list, tab = tab, endTextRes = endTextRes, onLoadMore = onLoadMore)
    }
}

private fun <T> LazyListScope.listFooter(
    list: SpaceListState<T>,
    tab: SpaceTab,
    endTextRes: Int,
    onLoadMore: (SpaceTab) -> Unit,
) {
    item(key = "footer") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                list.isLoading -> CircularProgressIndicator(Modifier.size(22.dp))

                list.hasNextPage ->
                    TextButton(onClick = { onLoadMore(tab) }) {
                        Text(stringResource(R.string.space_load_more))
                    }

                else ->
                    Text(
                        text = stringResource(endTextRes, list.items.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        topics = SpaceListState(items = previewTopics, loaded = true),
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
        selectedTab = SpaceTab.GENERAL,
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8a 我的主页 · 主题帖")
@Composable
private fun UserSpaceSelfPreview() {
    NodeSeekTheme { PreviewScreen(previewSelfState) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8b 公开用户页 · 概况")
@Composable
private fun UserSpacePublicPreview() {
    NodeSeekTheme { PreviewScreen(previewPublicState) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8a 我的主页 · dark")
@Composable
private fun UserSpaceSelfDarkPreview() {
    NodeSeekTheme(darkTheme = true) { PreviewScreen(previewSelfState) }
}

@Composable
private fun PreviewScreen(state: UserSpaceUiState) {
    UserSpaceScreen(
        state = state,
        onBack = {},
        onTabSelected = {},
        onPostClick = { _, _ -> },
        onLoadMore = {},
        onRetryTab = {},
        onRetryProfile = {},
        onMessage = {},
        onEditProfile = {},
        onOpenBrowser = {},
        onSignIn = {},
    )
}
