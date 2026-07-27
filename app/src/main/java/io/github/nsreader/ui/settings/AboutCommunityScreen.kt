package io.github.nsreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nsreader.R
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth
import kotlinx.coroutines.launch

/** Board f1: app identity, NodeSeek community links, and the project's legal surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutCommunityScreen(
    versionName: String,
    onBack: () -> Unit,
    onOpenAboutSite: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenUri: (String) -> Unit,
    onCopyRss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.about_rss_copied)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsBlock(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.about_version, versionName),
                top = true,
                bottom = true,
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
            ) {
                Text(
                    stringResource(R.string.about_app_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onOpenUri(CommunityLinks.RELEASES) }) {
                    Text(stringResource(R.string.about_check_updates))
                }
            }

            SettingsSectionTitle(stringResource(R.string.about_community))
            SettingsGroup {
                SettingsBlock(
                    title = stringResource(R.string.about_forum_stats),
                    subtitle = stringResource(R.string.about_forum_stats_snapshot),
                    top = true,
                ) {
                    Text(
                        stringResource(R.string.about_forum_stats_value),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                SettingsRow(
                    title = stringResource(R.string.about_site),
                    subtitle = stringResource(R.string.about_site_hint),
                    onClick = onOpenAboutSite,
                    trailing = { OpenInNewIcon() },
                )
                SettingsRow(
                    title = stringResource(R.string.about_privacy),
                    subtitle = stringResource(R.string.about_privacy_hint),
                    onClick = onOpenPrivacy,
                    trailing = { OpenInNewIcon() },
                )
                SettingsRow(
                    title = stringResource(R.string.about_rss),
                    subtitle = CommunityLinks.RSS,
                    bottom = true,
                    onClick = { onOpenUri(CommunityLinks.RSS) },
                    trailing = {
                        Row {
                            IconButton(
                                onClick = {
                                    onCopyRss()
                                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                                },
                            ) {
                                Icon(
                                    NodeSeekIcons.ContentCopy,
                                    contentDescription = stringResource(R.string.about_copy_rss),
                                )
                            }
                            OpenInNewIcon()
                        }
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.about_contact))
            SettingsGroup {
                ExternalRow(
                    title = stringResource(R.string.about_telegram_channel),
                    uri = CommunityLinks.TELEGRAM_CHANNEL,
                    onOpenUri = onOpenUri,
                    top = true,
                )
                ExternalRow(
                    title = stringResource(R.string.about_telegram_group),
                    uri = CommunityLinks.TELEGRAM_GROUP,
                    onOpenUri = onOpenUri,
                )
                ExternalRow(
                    title = stringResource(R.string.about_telegram_support),
                    uri = CommunityLinks.TELEGRAM_SUPPORT,
                    onOpenUri = onOpenUri,
                )
                ExternalRow(
                    title = stringResource(R.string.about_email),
                    subtitle = CommunityLinks.EMAIL.removePrefix("mailto:"),
                    uri = CommunityLinks.EMAIL,
                    onOpenUri = onOpenUri,
                    bottom = true,
                )
            }

            SettingsSectionTitle(stringResource(R.string.about_friends))
            SettingsGroup {
                ExternalRow(
                    title = stringResource(R.string.about_deepflood),
                    uri = CommunityLinks.DEEPFLOOD,
                    onOpenUri = onOpenUri,
                    top = true,
                )
                ExternalRow("LowEndTalk", CommunityLinks.LOW_END_TALK, onOpenUri = onOpenUri)
                ExternalRow("LowEndSpirit", CommunityLinks.LOW_END_SPIRIT, onOpenUri = onOpenUri)
                ExternalRow("HostLoc", CommunityLinks.HOST_LOC, onOpenUri = onOpenUri)
                ExternalRow(
                    title = "ServerHunter",
                    uri = CommunityLinks.SERVER_HUNTER,
                    onOpenUri = onOpenUri,
                    bottom = true,
                )
            }

            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_licenses),
                    subtitle = stringResource(R.string.about_licenses_hint),
                    top = true,
                    bottom = true,
                    onClick = onOpenLicenses,
                    leading = { Icon(NodeSeekIcons.Code, contentDescription = null) },
                )
            }
            Text(
                text = stringResource(R.string.about_unofficial_notice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun ExternalRow(
    title: String,
    uri: String,
    onOpenUri: (String) -> Unit,
    top: Boolean = false,
    bottom: Boolean = false,
    subtitle: String? = null,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle ?: uri.removePrefix("https://"),
        top = top,
        bottom = bottom,
        onClick = { onOpenUri(uri) },
        trailing = { OpenInNewIcon() },
    )
}

@Composable
private fun OpenInNewIcon() {
    Icon(
        NodeSeekIcons.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp),
    )
}

internal object CommunityLinks {
    const val RELEASES = "https://github.com/5151561/nsreader/releases/latest"
    const val RSS = "https://www.nodeseek.com/rss.xml"
    const val TELEGRAM_CHANNEL = "https://t.me/nodeseekc"
    const val TELEGRAM_GROUP = "https://t.me/nodeseekg"
    const val TELEGRAM_SUPPORT = "https://t.me/nodeseek"
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
            versionName = "1.0",
            onBack = {},
            onOpenAboutSite = {},
            onOpenPrivacy = {},
            onOpenLicenses = {},
            onOpenUri = {},
            onCopyRss = {},
        )
    }
}
