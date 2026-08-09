package io.github.nodyssey.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.plaza.designsys.component.GroupedColumn
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth

/**
 * 社区工具 — the six links NodeSeek keeps in its quick-access strip, grouped by what they are for.
 *
 * All six live behind one entry in 我的 rather than in the bottom bar: none of them is a daily
 * destination, and the four tabs are worth more to reading than to tools.
 *
 * The subtitles describe *form*, never state. An earlier draft claimed things like "今日还有 3 次抽奖"
 * and "邀请码余量 2" — numbers the site does not publish anywhere. A subtitle that says what a page is
 * ("加精帖列表 · 与首页同款") stays true without a request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityToolsScreen(
    onBack: () -> Unit,
    onAward: () -> Unit,
    onProviders: () -> Unit,
    onFriends: () -> Unit,
    onLucky: () -> Unit,
    onInvite: () -> Unit,
    onRuling: () -> Unit,
    onAboutCommunity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tools_title)) },
                scrollBehavior = scrollBehavior,
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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column {
                SectionLabel(stringResource(R.string.tools_group_browse))
                GroupedColumn {
                    GroupedRow(
                        title = stringResource(R.string.tools_award),
                        subtitle = stringResource(R.string.tools_award_subtitle),
                        icon = PlazaIcons.MenuBook,
                        first = true,
                        onClick = onAward,
                    )
                    GroupedRow(
                        title = stringResource(R.string.tools_providers),
                        subtitle = stringResource(R.string.tools_providers_subtitle),
                        icon = Icons.Default.ShoppingCart,
                        onClick = onProviders,
                    )
                    GroupedRow(
                        title = stringResource(R.string.tools_friends),
                        subtitle = stringResource(R.string.tools_friends_subtitle),
                        icon = PlazaIcons.Link,
                        last = true,
                        onClick = onFriends,
                    )
                }
            }

            Column {
                SectionLabel(stringResource(R.string.tools_group_play))
                GroupedColumn {
                    GroupedRow(
                        title = stringResource(R.string.tools_lucky),
                        subtitle = stringResource(R.string.tools_lucky_subtitle),
                        icon = PlazaIcons.Casino,
                        first = true,
                        onClick = onLucky,
                    )
                    GroupedRow(
                        title = stringResource(R.string.tools_invite),
                        subtitle = stringResource(R.string.tools_invite_subtitle),
                        icon = PlazaIcons.ConfirmationNumber,
                        last = true,
                        onClick = onInvite,
                    )
                }
            }

            Column {
                SectionLabel(stringResource(R.string.tools_group_watch))
                GroupedRow(
                    title = stringResource(R.string.tools_ruling),
                    subtitle = stringResource(R.string.tools_ruling_subtitle),
                    icon = PlazaIcons.Gavel,
                    first = true,
                    last = true,
                    onClick = onRuling,
                )
            }

            Text(
                text = stringResource(R.string.tools_footnote),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            GroupedRow(
                title = stringResource(R.string.about_community_title),
                subtitle = stringResource(R.string.about_community_hint),
                icon = Icons.Default.Info,
                first = true,
                last = true,
                onClick = onAboutCommunity,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "9a 社区工具")
@Composable
private fun CommunityToolsPreview() {
    PlazaTheme {
        CommunityToolsScreen(
            onBack = {},
            onAward = {},
            onProviders = {},
            onFriends = {},
            onLucky = {},
            onInvite = {},
            onRuling = {},
            onAboutCommunity = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "9a 社区工具 · dark")
@Composable
private fun CommunityToolsDarkPreview() {
    PlazaTheme(darkTheme = true) {
        CommunityToolsScreen(
            onBack = {},
            onAward = {},
            onProviders = {},
            onFriends = {},
            onLucky = {},
            onInvite = {},
            onRuling = {},
            onAboutCommunity = {},
        )
    }
}
