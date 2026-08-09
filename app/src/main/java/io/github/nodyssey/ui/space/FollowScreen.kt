package io.github.nodyssey.ui.space

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.FollowUser
import io.github.nodyssey.ui.common.LoadingState
import io.github.nodyssey.ui.common.NodeSeekErrorState
import io.github.nodyssey.ui.common.StatusView
import io.github.plaza.designsys.component.NodysseyIcons
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.NodysseyTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.StatusShapes
import io.github.plaza.designsys.theme.TABULAR_FIGURES

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.follow_title)) },
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(state.selectedTab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab == state.selectedTab,
                        onClick = { onTabSelected(tab) },
                        text = {
                            Text(
                                stringResource(
                                    when (tab) {
                                        FollowTab.FOLLOWING -> R.string.follow_tab_following
                                        FollowTab.FOLLOWERS -> R.string.follow_tab_followers
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            when {
                list.isLoading && list.items.isEmpty() -> LoadingState()

                list.error != null && list.items.isEmpty() ->
                    NodeSeekErrorState(
                        error = list.error,
                        onRetry = { onRetry(state.selectedTab) },
                        onOpenBrowser = {
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
                            FollowRow(user = user, onClick = { onUserClick(user.uid) })
                        }
                        item(key = "footer") {
                            Text(
                                text =
                                stringResource(
                                    when (state.selectedTab) {
                                        FollowTab.FOLLOWING -> R.string.follow_end_following
                                        FollowTab.FOLLOWERS -> R.string.follow_end_followers
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
        icon = NodysseyIcons.Group,
        shape = StatusShapes.Empty,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title =
        stringResource(
            when (tab) {
                FollowTab.FOLLOWING -> R.string.follow_empty_following_title
                FollowTab.FOLLOWERS -> R.string.follow_empty_followers_title
            },
        ),
        description =
        stringResource(
            when (tab) {
                FollowTab.FOLLOWING -> R.string.follow_empty_following_body
                FollowTab.FOLLOWERS -> R.string.follow_empty_followers_body
            },
        ),
    )
}

@Composable
private fun FollowRow(
    user: FollowUser,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        UserAvatar(url = user.avatarUrl, name = user.name, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.space_uid, user.uid),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
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
    NodysseyTheme {
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
    NodysseyTheme {
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
    NodysseyTheme(darkTheme = true) {
        FollowScreen(
            state =
            FollowUiState(
                followers = SpaceListState(),
                following = SpaceListState(error = NodeSeekError.NotWired),
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
