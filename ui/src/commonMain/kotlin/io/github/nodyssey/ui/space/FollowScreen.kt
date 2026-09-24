package io.github.nodyssey.ui.space

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.FollowUser
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.follow_empty_followers_body
import io.github.nodyssey.ui.resources.follow_empty_followers_title
import io.github.nodyssey.ui.resources.follow_empty_following_body
import io.github.nodyssey.ui.resources.follow_empty_following_title
import io.github.nodyssey.ui.resources.follow_end_followers
import io.github.nodyssey.ui.resources.follow_end_following
import io.github.nodyssey.ui.resources.follow_tab_followers
import io.github.nodyssey.ui.resources.follow_tab_followers_count
import io.github.nodyssey.ui.resources.follow_tab_following
import io.github.nodyssey.ui.resources.follow_tab_following_count
import io.github.nodyssey.ui.resources.follow_title
import io.github.nodyssey.ui.resources.space_uid
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.StatusView
import io.github.plaza.designsys.component.TabLabel
import io.github.plaza.designsys.component.UnderlineTabRow
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.StatusShapes
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun FollowRoute(
    viewModel: FollowViewModel,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FollowScreen(
        state = state,
        onBack = onBack,
        onTabSelected = viewModel::selectTab,
        onUserClick = onUserClick,
        onRetry = viewModel::retry,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowScreen(
    state: FollowUiState,
    onBack: () -> Unit,
    onTabSelected: (FollowTab) -> Unit,
    onUserClick: (Long) -> Unit,
    onRetry: (FollowTab) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = FollowTab.entries
    val list = state.listFor(state.selectedTab)

    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.follow_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // The centred column every page with a big title lays its content in, so on a tablet the
        // title heads these tabs and this list rather than sitting a few hundred dp in from them.
        Column(Modifier.padding(padding).fillMaxSize().readableWidth()) {
            // 9h's underline tabs, sitting flush on the page like the title above them. Each names its
            // count once its list has loaded: the endpoint answers with the whole list, so its length
            // is the site's own number, not a page's worth.
            UnderlineTabRow(
                selectedTabIndex = tabs.indexOf(state.selectedTab),
                tabs = tabs.map { TabLabel(it.label(state.listFor(it))) },
                onSelect = { onTabSelected(tabs[it]) },
                modifier = Modifier.padding(bottom = Spacing.md),
            )

            when {
                list.isLoading && list.items.isEmpty() -> LoadingState()

                list.error != null && list.items.isEmpty() ->
                    SiteErrorState(
                        error = list.error,
                        onRetry = { onRetry(state.selectedTab) },
                        // Named rather than reached by [SiteErrorState]'s fallback: the two are the
                        // same closure here, and a challenge arriving at a web view by fallback is how
                        // this stopped being a challenge web view on other screens.
                        onOpenBrowser = {
                            onOpenBrowser(
                                NodeSeekSite.BASE_URL +
                                    NodeSeekSite.fansPath(state.selectedTab == FollowTab.FOLLOWERS),
                            )
                        },
                        onVerify = {
                            onOpenBrowser(
                                NodeSeekSite.BASE_URL +
                                    NodeSeekSite.fansPath(state.selectedTab == FollowTab.FOLLOWERS),
                            )
                        },
                        onSignIn = onSignIn,
                    )

                list.items.isEmpty() -> FollowEmptyState(state.selectedTab)

                else ->
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(count = list.items.size, key = { list.items[it].uid }) { index ->
                            val user = list.items[index]
                            FollowRow(
                                user = user,
                                first = index == 0,
                                last = index == list.items.lastIndex,
                                onClick = { onUserClick(user.uid) },
                            )
                        }
                        item(key = "footer") {
                            Text(
                                text =
                                stringResource(
                                    when (state.selectedTab) {
                                        FollowTab.FOLLOWING -> Res.string.follow_end_following
                                        FollowTab.FOLLOWERS -> Res.string.follow_end_followers
                                    },
                                    list.items.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 22.dp),
                            )
                        }
                    }
            }
        }
    }
}

/**
 * The empty state uses the site's own sentence — "暂时没有用户关注您" — and offers no button.
 *
 * There is nothing to press: you cannot make someone follow you, and every other empty state in this
 * app has an action precisely because it has one to offer.
 */
@Composable
private fun FollowEmptyState(tab: FollowTab) {
    StatusView(
        icon = PlazaIcons.Group,
        shape = StatusShapes.Empty,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title =
        stringResource(
            when (tab) {
                FollowTab.FOLLOWING -> Res.string.follow_empty_following_title
                FollowTab.FOLLOWERS -> Res.string.follow_empty_followers_title
            },
        ),
        description =
        stringResource(
            when (tab) {
                FollowTab.FOLLOWING -> Res.string.follow_empty_following_body
                FollowTab.FOLLOWERS -> Res.string.follow_empty_followers_body
            },
        ),
    )
}

@Composable
private fun FollowTab.label(list: SpaceListState<FollowUser>): String =
    when {
        !list.loaded ->
            stringResource(
                when (this) {
                    FollowTab.FOLLOWING -> Res.string.follow_tab_following
                    FollowTab.FOLLOWERS -> Res.string.follow_tab_followers
                },
            )

        this == FollowTab.FOLLOWING -> stringResource(Res.string.follow_tab_following_count, list.items.size)

        else -> stringResource(Res.string.follow_tab_followers_count, list.items.size)
    }

/**
 * One account: avatar, name, UID. The site's list carries no relationship button (9h), and neither
 * does this — the row opens the account's space, where 关注 lives.
 *
 * The whole list is one white card, drawn a row at a time because the rows are lazy items.
 */
@Composable
private fun FollowRow(
    user: FollowUser,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    GroupedListItem(
        first = first,
        last = last,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = LayerPageGutter),
        leadingContent = { UserAvatar(url = user.avatarUrl, name = user.name, size = 40.dp) },
        headlineContent = { Text(user.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                stringResource(Res.string.space_uid, user.uid),
                style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        },
    )
}

// -------------------------------------------------------------------------------------------------

private val previewFollowing =
    listOf(
        FollowUser(4471, "nssk", null),
        FollowUser(302, "酒神", null),
        FollowUser(18754, "demain", null),
        FollowUser(27093, "羽落无声", null),
        FollowUser(9856, "ifreedom", null),
        FollowUser(33120, "jswcph", null),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8c 我的关注")
@Composable
private fun FollowListPreview() {
    PlazaTheme {
        FollowScreen(
            state =
            FollowUiState(
                selectedTab = FollowTab.FOLLOWING,
                following = SpaceListState(items = previewFollowing, loaded = true),
            ),
            onBack = {},
            onTabSelected = {},
            onUserClick = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8c 我的粉丝 · 空态")
@Composable
private fun FollowEmptyPreview() {
    PlazaTheme {
        FollowScreen(
            state =
            FollowUiState(
                selectedTab = FollowTab.FOLLOWERS,
                followers = SpaceListState(loaded = true),
            ),
            onBack = {},
            onTabSelected = {},
            onUserClick = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8c 接口待接入")
@Composable
private fun FollowNotWiredPreview() {
    PlazaTheme(darkTheme = true) {
        FollowScreen(
            state =
            FollowUiState(
                followers = SpaceListState(),
                following = SpaceListState(error = SiteError.NotWired),
            ),
            onBack = {},
            onTabSelected = {},
            onUserClick = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}
