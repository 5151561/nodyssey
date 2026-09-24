package io.github.nodyssey.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.NodeSeekStickers
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.composer.PostPermission
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.LockBadge
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.common.MediumButtonStyle
import io.github.nodyssey.ui.common.SiteErrorSnackbar
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.compactCount
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.lockBadgeDescription
import io.github.nodyssey.ui.common.postCardTitleStyle
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_more
import io.github.nodyssey.ui.resources.action_open_in_browser
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.assets_level
import io.github.nodyssey.ui.resources.post_badge_private
import io.github.nodyssey.ui.resources.post_reply_count
import io.github.nodyssey.ui.resources.post_view_count
import io.github.nodyssey.ui.resources.profile_edit
import io.github.nodyssey.ui.resources.space_comment_in
import io.github.nodyssey.ui.resources.space_empty_collections
import io.github.nodyssey.ui.resources.space_empty_comments
import io.github.nodyssey.ui.resources.space_empty_topics
import io.github.nodyssey.ui.resources.space_end_collections
import io.github.nodyssey.ui.resources.space_end_comments
import io.github.nodyssey.ui.resources.space_end_topics
import io.github.nodyssey.ui.resources.space_follow
import io.github.nodyssey.ui.resources.space_following
import io.github.nodyssey.ui.resources.space_message
import io.github.nodyssey.ui.resources.space_readme
import io.github.nodyssey.ui.resources.space_readme_collapse
import io.github.nodyssey.ui.resources.space_readme_empty
import io.github.nodyssey.ui.resources.space_readme_expand
import io.github.nodyssey.ui.resources.space_stat_chicken
import io.github.nodyssey.ui.resources.space_stat_comments
import io.github.nodyssey.ui.resources.space_stat_joined_days
import io.github.nodyssey.ui.resources.space_stat_topics
import io.github.nodyssey.ui.resources.space_tab_collections
import io.github.nodyssey.ui.resources.space_tab_comments
import io.github.nodyssey.ui.resources.space_tab_general
import io.github.nodyssey.ui.resources.space_tab_topics
import io.github.nodyssey.ui.resources.space_uid
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.richtext.collapseMarkdown
import io.github.plaza.core.richtext.parseMarkdown
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerCardGap
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.MetaStat
import io.github.plaza.designsys.component.PillTabRow
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.TabLabel
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserSpaceRoute(
    viewModel: UserSpaceViewModel,
    onBack: () -> Unit,
    onPostClick: (Long, String?) -> Unit,
    /** Opens the conversation with this account: its uid, and the name its thread should title. */
    onMessage: (Long, String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** Clears a Cloudflare challenge on the page being read, and comes back on its own. */
    onVerify: (String) -> Unit,
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
        onMessage = { onMessage(state.uid, state.name) },
        onToggleFollow = viewModel::toggleFollow,
        onFollowFailureShown = viewModel::onFollowFailureShown,
        onEditProfile = onEditProfile,
        onOpenBrowser = onOpenBrowser,
        onLinkClick = onLinkClick,
        onSignIn = onSignIn,
        onVerify = onVerify,
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
    /** Clears a Cloudflare challenge on this space's own URL. */
    onVerify: (String) -> Unit,
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
        onVerify = { onVerify(spaceUrl) },
        onRetry = onToggleFollow,
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
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.isSelf) {
                        IconButton(onClick = onEditProfile) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.profile_edit),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    SpaceOverflowMenu(onOpenBrowser = { onOpenBrowser(spaceUrl) })
                },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.isLoadingProfile && !state.hasProfile) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (state.error != null && !state.hasProfile) {
            SiteErrorState(
                error = state.error,
                onRetry = onRetryProfile,
                onOpenBrowser = { onOpenBrowser(spaceUrl) },
                onSignIn = onSignIn,
                onVerify = { onVerify(spaceUrl) },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        // One scrolling page, header card included (3a): the header grew into a card with the bio,
        // the stats and both actions on it, and pinned above a list it would leave the list a strip.
        // The tabs are the exception: they stick once the header has gone, so a reader forty topics
        // down can still switch to 评论.
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        LazyColumn(
            state = listState,
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
            contentPadding = PaddingValues(start = LayerPageGutter, end = LayerPageGutter, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(LayerCardGap),
        ) {
            item(key = "header") {
                SpaceHeader(state = state, onMessage = onMessage, onToggleFollow = onToggleFollow)
            }
            stickyHeader(key = "tabs") {
                SpaceTabs(
                    state = state,
                    onTabSelected = { tab ->
                        onTabSelected(tab)
                        // Switched while stuck: the new tab starts at its top, under the tabs. Left
                        // alone, the list would keep the old tab's row index and open the new one
                        // somewhere in its middle.
                        if (listState.firstVisibleItemIndex >= TABS_INDEX) {
                            scope.launch { listState.scrollToItem(TABS_INDEX) }
                        }
                    },
                    // The page's own colour, so rows scrolling under the stuck tabs do not show
                    // through between the pills.
                    modifier = Modifier.background(LocalPlazaLayers.current.page).padding(vertical = 2.dp),
                )
            }
            spaceTabContent(
                state = state,
                topics = topics,
                comments = comments,
                collections = collections,
                onPostClick = onPostClick,
                onOpenBrowser = onOpenBrowser,
                onLinkClick = onLinkClick,
                onSignIn = onSignIn,
                onVerify = onVerify,
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
                PlazaIcons.OpenInNew,
                contentDescription = stringResource(Res.string.action_more),
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_open_in_browser)) },
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
 * stands in for the failures that never reached the site. The walls are the exception that gets an
 * action instead of a sentence, because they are the failures the reader can clear from here: this
 * used to offer 登录 and nothing else, so a follow refused by Cloudflare read as a flat statement
 * with no way to act on it.
 */
@Composable
private fun FollowFailureEffect(
    failure: FollowFailure?,
    snackbarHostState: SnackbarHostState,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onRetry: () -> Unit,
    onShown: () -> Unit,
) {
    SiteErrorSnackbar(
        error = failure?.error,
        snackbarHostState = snackbarHostState,
        onShown = onShown,
        detail = failure?.detail,
        onVerify = onVerify,
        onSignIn = onSignIn,
        onRetry = onRetry,
    )
}

/**
 * 3a's header card: who (avatar, name, level, UID), what they say about themselves (the bio, now here
 * rather than a card of its own under 概况, so it is read before any tab is chosen), what the site
 * counts for them, and — on someone else's page — the two things you can do about them.
 *
 * 3a also tags the account with a role (「服主」). The profile payload carries no role, so there is no
 * tag to draw.
 */
@Composable
private fun SpaceHeader(
    state: UserSpaceUiState,
    onMessage: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    LayerCard(
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UserAvatar(url = state.avatarUrl, name = state.name, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    state.level?.let { level ->
                        TonalTag(
                            text = stringResource(Res.string.assets_level, level),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.space_uid, state.uid),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.bio?.let { bio ->
            Text(bio, style = MaterialTheme.typography.bodyMedium)
        }
        SpaceStatsRow(state)
        // The two actions the site offers on someone else's space, in the site's own order.
        if (!state.isSelf) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canFollow) {
                    FollowButton(
                        followed = state.followed == true,
                        onClick = onToggleFollow,
                        modifier = Modifier.weight(1f),
                    )
                }
                MediumButton(
                    onClick = onMessage,
                    style = MediumButtonStyle.Tonal,
                    icon = Icons.Default.Email,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.space_message))
                }
            }
        }
    }
}

/**
 * 关注 / 已关注, as one button that says what pressing it will do next.
 *
 * Filled while unfollowed and tonal once followed, which is the same demotion the site performs by
 * turning its blue button grey: after the relationship exists, undoing it is not the action this screen
 * is for. The label is the *current* state rather than the pending action — "已关注" on a quieter button
 * reads as a toggle that is on, where "取关" would read as a warning.
 */
@Composable
private fun FollowButton(
    followed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediumButton(
        onClick = onClick,
        style = if (followed) MediumButtonStyle.Tonal else MediumButtonStyle.Filled,
        icon = if (followed) Icons.Default.Check else Icons.Default.Add,
        modifier = modifier,
    ) {
        Text(stringResource(if (followed) Res.string.space_following else Res.string.space_follow))
    }
}

/**
 * The statistics the site actually publishes for an account, on an inset tile.
 *
 * 加入天数 / 鸡腿 / 主题帖 / 评论 — the level, the fifth, rides on the tag beside the name in 3a rather
 * than taking a column. `getInfo` does also carry `fans` and `follows`, but the two lists they count
 * already have a screen of their own (关注与粉丝), and the site's own page does not print them.
 */
@Composable
private fun SpaceStatsRow(state: UserSpaceUiState) {
    Surface(color = LocalPlazaLayers.current.inset, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(vertical = 12.dp)) {
            SpaceStat(state.joinedDays?.toString(), stringResource(Res.string.space_stat_joined_days))
            SpaceStat(state.chickenCount?.formatted(), stringResource(Res.string.space_stat_chicken))
            SpaceStat(state.topicCount?.formatted(), stringResource(Res.string.space_stat_topics))
            SpaceStat(state.commentCount?.formatted(), stringResource(Res.string.space_stat_comments))
        }
    }
}

@Composable
private fun RowScope.SpaceStat(
    value: String?,
    label: String,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value ?: UNKNOWN_VALUE,
            style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val UNKNOWN_VALUE = "—"

// Grouped by hand rather than through `"%,d".format`, which is a JVM extension. Only ever asked
// about a count, so the negative case the grouping would get wrong cannot arrive.
private fun Int.formatted(): String =
    if (this >= 1_000) toString().reversed().chunked(3).joinToString(",").reversed() else toString()

/** 3a's tabs, as [PillTabRow] draws them. */
@Composable
private fun SpaceTabs(
    state: UserSpaceUiState,
    onTabSelected: (SpaceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PillTabRow(
        selectedTabIndex = state.tabs.indexOf(state.selectedTab).coerceAtLeast(0),
        tabs = state.tabs.map { TabLabel(stringResource(it.labelRes())) },
        onSelect = { onTabSelected(state.tabs[it]) },
        modifier = modifier,
    )
}

/** The tabs' place in the list: right after the header card. */
private const val TABS_INDEX = 1

private fun LazyListScope.spaceTabContent(
    state: UserSpaceUiState,
    topics: LazyPagingItems<SpacePost>?,
    comments: LazyPagingItems<SpaceComment>?,
    collections: LazyPagingItems<SpacePost>?,
    onPostClick: (Long, String?) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
) {
    when (state.selectedTab) {
        SpaceTab.GENERAL -> item(key = "readme") { ReadmeCard(state, onLinkClick) }

        SpaceTab.TOPICS ->
            spaceListTab(
                list = topics,
                emptyText = Res.string.space_empty_topics,
                endTextRes = Res.string.space_end_topics,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                onVerify = onVerify,
                key = { _, post -> "topic-${post.postId}" },
            ) { post ->
                SpacePostRow(post = post, onClick = { onPostClick(post.postId, null) })
            }

        SpaceTab.COMMENTS ->
            spaceListTab(
                list = comments,
                emptyText = Res.string.space_empty_comments,
                endTextRes = Res.string.space_end_comments,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                onVerify = onVerify,
                // The payload's own id when it has one; the position only for the rare row without.
                key = { index, comment -> "comment-${comment.commentId ?: "at-$index"}" },
            ) { comment ->
                // The floor rides along so the thread opens at this very comment.
                SpaceCommentRow(
                    comment = comment,
                    onClick = { onPostClick(comment.postId, comment.floor) },
                )
            }

        SpaceTab.COLLECTIONS ->
            spaceListTab(
                list = collections,
                emptyText = Res.string.space_empty_collections,
                endTextRes = Res.string.space_end_collections,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                onVerify = onVerify,
                key = { _, post -> "collection-${post.postId}" },
            ) { post ->
                SpacePostRow(post = post, onClick = { onPostClick(post.postId, null) })
            }
    }
}

/**
 * 概况's Readme, in a card that folds long ones away.
 *
 * The fold is offered twice, in 3a's two places: a chevron on the card's heading and 展开全部 under the
 * text. Both do the same thing; the chevron is where the eye lands first, the text button where the
 * thumb is after reading the preview.
 */
@Composable
private fun ReadmeCard(
    state: UserSpaceUiState,
    onOpenBrowser: (String) -> Unit,
) {
    var readmeExpanded by rememberSaveable(state.uid) { mutableStateOf(false) }
    val readme = state.readme?.takeIf { it.isNotBlank() }
    val foldable = readme != null && readme.lines().size > README_COLLAPSED_LINES
    val toggleLabel =
        stringResource(if (readmeExpanded) Res.string.space_readme_collapse else Res.string.space_readme_expand)
    LayerCard(
        contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.space_readme),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (foldable) {
                IconButton(onClick = { readmeExpanded = !readmeExpanded }) {
                    Icon(
                        if (readmeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = toggleLabel,
                    )
                }
            } else {
                Box(Modifier.height(Sizes.minTouchTarget))
            }
        }
        Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (readme == null) {
                // The site's own empty state, emoji included. Writing our own would have been a
                // worse sentence and a less familiar one.
                Text(
                    stringResource(Res.string.space_readme_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            } else {
                // Readmes arrive as source, the same as 私信 do, so a `:ac01:` in one reaches the
                // renderer unexpanded. Whether the site's own space page draws it as a sticker has
                // not been checked against a logged-in session — this is the app answering the way
                // it answers everywhere else rather than a match confirmed field by field.
                val nodes = remember(readme, readmeExpanded) {
                    val markdown =
                        if (readmeExpanded) readme else collapseMarkdown(readme, README_COLLAPSED_LINES)
                    parseMarkdown(markdown, NodeSeekStickers::urlFor)
                }
                PostRichContent(
                    nodes = nodes,
                    onLinkClick = onOpenBrowser,
                    onImageClick = onOpenBrowser,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                if (foldable) {
                    TextButton(onClick = { readmeExpanded = !readmeExpanded }) { Text(toggleLabel) }
                } else {
                    Box(Modifier.height(10.dp))
                }
            }
        }
    }
}

private const val README_COLLAPSED_LINES = 8

/**
 * The list tabs, which differ only in their row and their end-of-list sentence.
 *
 * Emitted into the page's own `LazyColumn` rather than as a list of their own, so the header card
 * scrolls away with the rows. The repository remains page-numbered, while Paging 3 owns loading,
 * retries and append state.
 */
private fun <T : Any> LazyListScope.spaceListTab(
    list: LazyPagingItems<T>?,
    emptyText: StringResource,
    endTextRes: StringResource,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    /** Stable row identity, so a page-1 replace recomposes only the rows that actually changed. */
    key: (index: Int, item: T) -> Any,
    row: @Composable (T) -> Unit,
) {
    if (list == null || list.itemCount == 0) {
        item(key = "list-state") {
            val stateModifier = Modifier.fillParentMaxHeight(0.55f).fillMaxWidth()
            when (val refresh = list?.loadState?.refresh ?: LoadState.Loading) {
                LoadState.Loading -> LoadingState(stateModifier)

                is LoadState.Error ->
                    SiteErrorState(
                        error = refresh.error.toSiteError(),
                        onRetry = { list?.retry() },
                        onOpenBrowser = { onOpenBrowser(NodeSeekSite.BASE_URL) },
                        onSignIn = onSignIn,
                        onVerify = { onVerify(NodeSeekSite.BASE_URL) },
                        // A whole viewport, not the loading state's share of one: the error card
                        // with its two buttons is taller than 55% of a phone, and cut off at the
                        // item's edge its 重试 was only reachable by scrolling inside the card. At a
                        // viewport's height it fits, and the page itself scrolls down to it.
                        modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
                    )

                is LoadState.NotLoading ->
                    Box(stateModifier, contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(emptyText),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
        }
        return
    }

    items(
        count = list.itemCount,
        key = { index -> list.peek(index)?.let { key(index, it) } ?: "row-$index" },
    ) { index ->
        list[index]?.let { item -> row(item) }
    }
    item(key = "footer") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (list.loadState.append) {
                LoadState.Loading -> PlazaSpinner(Modifier.describedAsLoading(), size = 22.dp)

                is LoadState.Error ->
                    TextButton(onClick = list::retry) {
                        Text(stringResource(Res.string.action_retry))
                    }

                is LoadState.NotLoading ->
                    if (list.loadState.append.endOfPaginationReached) {
                        Text(
                            text = stringResource(endTextRes, list.itemCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        PlazaSpinner(Modifier.describedAsLoading(), size = 22.dp)
                    }
            }
        }
    }
}

/**
 * One post as a card, the way the home feed draws one: the 17/25 title, then its board and counts.
 *
 * Shared with 我的主题帖, which is the same card without the tabs around it.
 */
@Composable
internal fun SpacePostRow(
    post: SpacePost,
    onClick: () -> Unit,
) {
    LayerCard(onClick = onClick, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = post.title,
                style = postCardTitleStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Not filling, so the lock keeps its place beside the title rather than being
                // pushed off the end — the same arrangement the feed's title line uses.
                modifier = Modifier.weight(1f, fill = false),
            )
            // 私有 is a lock with nothing to count: the level is not a floor a reader can climb to,
            // it is the author alone. Every other restricted rank names the level it wants.
            if (post.permission != PostPermission.PUBLIC) {
                val level = post.permission.requiredLevel
                LockBadge(
                    level = level,
                    description =
                    if (level == null) {
                        stringResource(Res.string.post_badge_private)
                    } else {
                        lockBadgeDescription(level)
                    },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            post.categoryTitle?.let { BoardTag(title = it, slug = post.categorySlug) }
            post.authorName?.let { RowMeta(it) }
            post.commentCount?.let {
                MetaStat(
                    icon = PlazaIcons.ModeComment,
                    value = it.toString(),
                    contentDescription = stringResource(Res.string.post_reply_count, it),
                )
            }
            post.viewCount?.let {
                MetaStat(
                    icon = PlazaIcons.Visibility,
                    value = compactCount(it),
                    contentDescription = stringResource(Res.string.post_view_count, it),
                )
            }
            Box(Modifier.weight(1f))
            post.createdAtText?.let { RowMeta(it) }
        }
    }
}

@Composable
private fun SpaceCommentRow(
    comment: SpaceComment,
    onClick: () -> Unit,
) {
    LayerCard(onClick = onClick, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = comment.excerpt,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            comment.postTitle?.let {
                RowMeta(stringResource(Res.string.space_comment_in, it), modifier = Modifier.weight(1f))
            }
            comment.createdAtText?.let { RowMeta(it) }
        }
    }
}

@Composable
private fun RowMeta(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun SpaceTab.labelRes(): StringResource =
    when (this) {
        SpaceTab.GENERAL -> Res.string.space_tab_general
        SpaceTab.TOPICS -> Res.string.space_tab_topics
        SpaceTab.COMMENTS -> Res.string.space_tab_comments
        SpaceTab.COLLECTIONS -> Res.string.space_tab_collections
    }

// -------------------------------------------------------------------------------------------------

private val previewTopics =
    listOf(
        SpacePost(1, "第一次用 nftables 做端口转发，记录一下踩的坑", "技术", "tech", null, 12, 843, "3天前"),
        SpacePost(2, "签到鸡腿是随机的吗？连续七天都是 6 个", "日常", "daily", null, 18, 976, "上周"),
        SpacePost(3, "求推荐一台香港小鸡，跑 uptime 监控用", "日常", "daily", null, 21, 1024, "6月11日"),
        SpacePost(4, "【出】甲骨文 ARM 一台求接手", "交易", "trade", null, 6, 412, "5月2日", PostPermission(3)),
        SpacePost(
            5,
            "Debian 12 升 13 之后 ss 命令输出格式变了？",
            "技术",
            "tech",
            null,
            9,
            655,
            "4月20日",
            PostPermission.PRIVATE,
        ),
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
    PlazaTheme { PreviewScreen(previewSelfState) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8b 公开用户页 · 概况")
@Composable
private fun UserSpacePublicPreview() {
    PlazaTheme { PreviewScreen(previewPublicState) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8a 我的主页 · dark")
@Composable
private fun UserSpaceSelfDarkPreview() {
    PlazaTheme(darkTheme = true) { PreviewScreen(previewSelfState) }
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
        onVerify = {},
    )
}
