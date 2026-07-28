package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth
import kotlinx.coroutines.launch

@Composable
fun AboutCommunityRoute(
    viewModel: AboutCommunityViewModel,
    onBack: () -> Unit,
    onOpenAboutSite: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenUri: (String) -> Unit,
    onCopyRss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statsState by viewModel.uiState.collectAsStateWithLifecycle()
    AboutCommunityScreen(
        statsState = statsState,
        onBack = onBack,
        onOpenAboutSite = onOpenAboutSite,
        onOpenPrivacy = onOpenPrivacy,
        onOpenUri = onOpenUri,
        onCopyRss = onCopyRss,
        onRetryStats = viewModel::retry,
        modifier = modifier,
    )
}

/** NodeSeek 社区信息页；仅从社区工具进入，与本软件的关于页保持独立。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutCommunityScreen(
    onBack: () -> Unit,
    onOpenAboutSite: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenUri: (String) -> Unit,
    onCopyRss: () -> Unit,
    modifier: Modifier = Modifier,
    statsState: CommunityStatsUiState? = null,
    onRetryStats: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.about_rss_copied)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.about_community_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
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
        ) {
            SectionLabel(stringResource(R.string.about_community))
            statsState?.let { state ->
                CommunityStats(
                    state = state,
                    onRetry = onRetryStats,
                )
            }
            AboutActionRow(
                title = stringResource(R.string.about_site),
                subtitle = stringResource(R.string.about_site_hint),
                icon = NodysseyIcons.Article,
                onClick = onOpenAboutSite,
            )
            AboutActionRow(
                title = stringResource(R.string.about_privacy),
                subtitle = stringResource(R.string.about_privacy_hint),
                icon = Icons.Default.Info,
                onClick = onOpenPrivacy,
            )
            AboutActionRow(
                title = stringResource(R.string.about_rss),
                subtitle = CommunityLinks.RSS_DISPLAY,
                icon = NodysseyIcons.Article,
                trailing = {
                    Icon(
                        NodysseyIcons.ContentCopy,
                        contentDescription = stringResource(R.string.about_copy_rss),
                    )
                },
                onClick = {
                    onCopyRss()
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                },
            )

            SectionLabel(stringResource(R.string.about_contact))
            AboutActionRow(
                title = stringResource(R.string.about_telegram_channel),
                icon = NodysseyIcons.Campaign,
                external = true,
                onClick = { onOpenUri(CommunityLinks.TELEGRAM_CHANNEL) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_telegram_group),
                icon = NodysseyIcons.Group,
                external = true,
                onClick = { onOpenUri(CommunityLinks.TELEGRAM_GROUP) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_email),
                subtitle = CommunityLinks.EMAIL.removePrefix("mailto:"),
                icon = NodysseyIcons.Campaign,
                external = true,
                onClick = { onOpenUri(CommunityLinks.EMAIL) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_deepflood),
                subtitle = stringResource(R.string.about_deepflood_hint),
                icon = NodysseyIcons.Group,
                external = true,
                onClick = { onOpenUri(CommunityLinks.DEEPFLOOD) },
            )
            FriendSiteChips(onOpenUri)
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.xs).semantics { heading() },
    )
}

@Composable
private fun CommunityStats(
    state: CommunityStatsUiState,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(NodysseyIcons.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text =
                    when (state) {
                        CommunityStatsUiState.Loading -> stringResource(R.string.about_forum_stats_loading)
                        is CommunityStatsUiState.Content -> stringResource(R.string.about_forum_stats_value, state.memberCount)
                        CommunityStatsUiState.Error -> stringResource(R.string.about_forum_stats_error)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state is CommunityStatsUiState.Content) {
                    Text(
                        stringResource(R.string.about_forum_stats_snapshot),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (state) {
                CommunityStatsUiState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )

                CommunityStatsUiState.Error ->
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }

                is CommunityStatsUiState.Content -> Unit
            }
        }
    }
}

@Composable
private fun FriendSiteChips(onOpenUri: (String) -> Unit) {
    val sites = listOf(
        "LowEndTalk" to CommunityLinks.LOW_END_TALK,
        "LowEndSpirit" to CommunityLinks.LOW_END_SPIRIT,
        "HostLoc" to CommunityLinks.HOST_LOC,
        "ServerHunter" to CommunityLinks.SERVER_HUNTER,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        sites.forEach { (name, uri) ->
            Surface(
                onClick = { onOpenUri(uri) },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(50),
                modifier = Modifier.widthIn(min = 48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Icon(NodysseyIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

internal object CommunityLinks {
    const val RSS = "https://www.nodeseek.com/rss.xml"
    const val RSS_DISPLAY = "rss.nodeseek.com"
    const val TELEGRAM_CHANNEL = "https://t.me/nodeseekc"
    const val TELEGRAM_GROUP = "https://t.me/nodeseekg"
    const val EMAIL = "mailto:Lloyd@nodeseek.com"
    const val DEEPFLOOD = "https://www.deepflood.com"
    const val LOW_END_TALK = "https://lowendtalk.com"
    const val LOW_END_SPIRIT = "https://lowendspirit.com"
    const val HOST_LOC = "https://hostloc.com"
    const val SERVER_HUNTER = "https://www.serverhunter.com"
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "f1 关于 · 社区")
@Composable
private fun AboutCommunityPreview() {
    NodysseyTheme {
        AboutCommunityScreen(
            onBack = {},
            onOpenAboutSite = {},
            onOpenPrivacy = {},
            onOpenUri = {},
            onCopyRss = {},
        )
    }
}
