package io.github.nsreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nsreader.R
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth
import kotlinx.coroutines.launch

sealed interface AppUpdateStatus {
    data object Unknown : AppUpdateStatus

    data object Latest : AppUpdateStatus

    data class Available(val version: String) : AppUpdateStatus
}

/** f1：本软件与社区是同一滚动页，依设计稿保留两屏的内容节奏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutCommunityScreen(
    versionName: String,
    versionCode: Long,
    updateStatus: AppUpdateStatus,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenAboutSite: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenUri: (String) -> Unit,
    onCopyRss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val communityTitleThreshold = with(LocalDensity.current) { 520.dp.roundToPx() }
    val communitySectionVisible by remember(scrollState, communityTitleThreshold) {
        derivedStateOf { scrollState.value > communityTitleThreshold }
    }
    val copiedMessage = stringResource(R.string.about_rss_copied)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (communitySectionVisible) R.string.about_community_title else R.string.about_title),
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
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.lg),
        ) {
            AppIdentity(
                versionName = versionName,
                versionCode = versionCode,
                updateStatus = updateStatus,
                onCheckUpdates = onCheckUpdates,
            )
            UnofficialNotice()
            AboutActionRow(
                title = stringResource(R.string.about_project_home),
                subtitle = "github.com/5151561/nsreader",
                icon = NodeSeekIcons.Code,
                external = true,
                onClick = { onOpenUri(CommunityLinks.PROJECT_HOME) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_feedback),
                subtitle = stringResource(R.string.about_feedback_hint),
                icon = NodeSeekIcons.Campaign,
                external = true,
                onClick = { onOpenUri(CommunityLinks.ISSUES) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_changelog),
                icon = NodeSeekIcons.History,
                onClick = onOpenChangelog,
            )
            AboutActionRow(
                title = stringResource(R.string.settings_licenses),
                subtitle = stringResource(R.string.about_licenses_hint),
                icon = NodeSeekIcons.Code,
                onClick = onOpenLicenses,
            )
            Text(
                stringResource(R.string.about_theme_signature),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            SectionLabel(stringResource(R.string.about_community))
            CommunityStats()
            AboutActionRow(
                title = stringResource(R.string.about_site),
                subtitle = stringResource(R.string.about_site_hint),
                icon = NodeSeekIcons.Article,
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
                icon = NodeSeekIcons.Article,
                trailing = {
                    Icon(
                        NodeSeekIcons.ContentCopy,
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
                icon = NodeSeekIcons.Campaign,
                external = true,
                onClick = { onOpenUri(CommunityLinks.TELEGRAM_CHANNEL) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_telegram_group),
                icon = NodeSeekIcons.Group,
                external = true,
                onClick = { onOpenUri(CommunityLinks.TELEGRAM_GROUP) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_email),
                subtitle = CommunityLinks.EMAIL.removePrefix("mailto:"),
                icon = NodeSeekIcons.Campaign,
                external = true,
                onClick = { onOpenUri(CommunityLinks.EMAIL) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_deepflood),
                subtitle = stringResource(R.string.about_deepflood_hint),
                icon = NodeSeekIcons.Group,
                external = true,
                onClick = { onOpenUri(CommunityLinks.DEEPFLOOD) },
            )
            FriendSiteChips(onOpenUri)
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun AppIdentity(
    versionName: String,
    versionCode: Long,
    updateStatus: AppUpdateStatus,
    onCheckUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Surface(
            modifier = Modifier.size(76.dp),
            shape = RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("NS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.about_version, versionName, versionCode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(
                onClick = onCheckUpdates,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(NodeSeekIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.about_check_updates))
            }
            Text(
                when (updateStatus) {
                    AppUpdateStatus.Unknown -> stringResource(R.string.about_update_unknown)
                    AppUpdateStatus.Latest -> stringResource(R.string.about_update_latest)
                    is AppUpdateStatus.Available -> stringResource(R.string.about_update_available, updateStatus.version)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (updateStatus is AppUpdateStatus.Available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (updateStatus is AppUpdateStatus.Available) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun UnofficialNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.about_unofficial_notice),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    external: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.background) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = if (it.contains("github.com") || it.contains("@")) FontFamily.Monospace else FontFamily.Default,
                        )
                    }
                }
                when {
                    trailing != null -> trailing()
                    external -> Icon(NodeSeekIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun CommunityStats() {
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
            Icon(NodeSeekIcons.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(stringResource(R.string.about_forum_stats_value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_forum_stats_snapshot), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Icon(NodeSeekIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

internal object CommunityLinks {
    const val PROJECT_HOME = "https://github.com/5151561/nsreader"
    const val ISSUES = "https://github.com/5151561/nsreader/issues"
    const val RELEASES = "https://github.com/5151561/nsreader/releases"
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

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "f1 关于与社区")
@Composable
private fun AboutCommunityPreview() {
    NodeSeekTheme {
        AboutCommunityScreen(
            versionName = "1.0.0",
            versionCode = 100,
            updateStatus = AppUpdateStatus.Latest,
            onBack = {},
            onCheckUpdates = {},
            onOpenAboutSite = {},
            onOpenPrivacy = {},
            onOpenChangelog = {},
            onOpenLicenses = {},
            onOpenUri = {},
            onCopyRss = {},
        )
    }
}
