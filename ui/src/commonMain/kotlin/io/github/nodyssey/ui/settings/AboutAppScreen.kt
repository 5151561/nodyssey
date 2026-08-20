package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.ui.common.rememberFileSizeLabel
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.about_app_channel
import io.github.nodyssey.ui.resources.about_app_channel_hint
import io.github.nodyssey.ui.resources.about_app_group
import io.github.nodyssey.ui.resources.about_app_group_hint
import io.github.nodyssey.ui.resources.about_changelog
import io.github.nodyssey.ui.resources.about_check_updates
import io.github.nodyssey.ui.resources.about_feedback
import io.github.nodyssey.ui.resources.about_feedback_hint
import io.github.nodyssey.ui.resources.about_install_failed_blocked
import io.github.nodyssey.ui.resources.about_install_failed_conflict
import io.github.nodyssey.ui.resources.about_install_failed_incompatible
import io.github.nodyssey.ui.resources.about_install_failed_invalid
import io.github.nodyssey.ui.resources.about_install_failed_storage
import io.github.nodyssey.ui.resources.about_install_failed_unknown
import io.github.nodyssey.ui.resources.about_licenses_hint
import io.github.nodyssey.ui.resources.about_project_home
import io.github.nodyssey.ui.resources.about_theme_signature
import io.github.nodyssey.ui.resources.about_title
import io.github.nodyssey.ui.resources.about_unofficial_notice
import io.github.nodyssey.ui.resources.about_update_available
import io.github.nodyssey.ui.resources.about_update_cancel
import io.github.nodyssey.ui.resources.about_update_checking
import io.github.nodyssey.ui.resources.about_update_download
import io.github.nodyssey.ui.resources.about_update_downloading
import io.github.nodyssey.ui.resources.about_update_downloading_unknown
import io.github.nodyssey.ui.resources.about_update_failed_checksum
import io.github.nodyssey.ui.resources.about_update_failed_network
import io.github.nodyssey.ui.resources.about_update_failed_server
import io.github.nodyssey.ui.resources.about_update_failed_storage
import io.github.nodyssey.ui.resources.about_update_failed_unreadable
import io.github.nodyssey.ui.resources.about_update_install
import io.github.nodyssey.ui.resources.about_update_latest
import io.github.nodyssey.ui.resources.about_update_new_version
import io.github.nodyssey.ui.resources.about_update_open_release
import io.github.nodyssey.ui.resources.about_update_permission_action
import io.github.nodyssey.ui.resources.about_update_permission_hint
import io.github.nodyssey.ui.resources.about_update_prerelease
import io.github.nodyssey.ui.resources.about_update_prerelease_hint
import io.github.nodyssey.ui.resources.about_update_retry
import io.github.nodyssey.ui.resources.about_update_unknown
import io.github.nodyssey.ui.resources.about_version
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.settings_licenses
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallFailure
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateDownload
import io.github.plaza.core.update.UpdateFailure
import io.github.plaza.core.update.releaseNotesText
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun AboutAppRoute(
    viewModel: AboutAppViewModel,
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestInstallPermission = rememberInstallPermissionRequest(viewModel::onInstallPermissionResult)

    AboutAppScreen(
        state = state,
        onBack = onBack,
        onCheckUpdates = viewModel::checkForUpdates,
        onDownloadUpdate = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onInstallUpdate = viewModel::install,
        onGrantInstallPermission = requestInstallPermission,
        onOpenChangelog = onOpenChangelog,
        onOpenLicenses = onOpenLicenses,
        onOpenUri = onOpenUri,
        modifier = modifier,
    )
}

/** 本软件的关于页；站点与社区信息由 [AboutCommunityScreen] 独立承载。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppScreen(
    state: AboutAppUiState,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallUpdate: () -> Unit,
    onGrantInstallPermission: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.about_title),
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            AppIdentity(
                appName = state.appName,
                versionName = state.versionName,
                versionCode = state.versionCode,
                check = state.update.check,
                onCheckUpdates = onCheckUpdates,
            )
            state.update.available?.let { release ->
                UpdateCard(
                    release = release,
                    download = state.update.download,
                    installFailure = state.update.installFailure,
                    needsInstallPermission = state.needsInstallPermission,
                    onDownload = onDownloadUpdate,
                    onCancelDownload = onCancelDownload,
                    onInstall = onInstallUpdate,
                    onGrantInstallPermission = onGrantInstallPermission,
                    onOpenRelease = { onOpenUri(release.htmlUrl) },
                )
            }
            UnofficialNotice()
            AboutActionRow(
                title = stringResource(Res.string.about_project_home),
                subtitle = "github.com/5151561/nodyssey",
                icon = PlazaIcons.Code,
                external = true,
                onClick = { onOpenUri(AppLinks.PROJECT_HOME) },
            )
            AboutActionRow(
                title = stringResource(Res.string.about_feedback),
                subtitle = stringResource(Res.string.about_feedback_hint),
                icon = PlazaIcons.Campaign,
                external = true,
                onClick = { onOpenUri(AppLinks.ISSUES) },
            )
            // Sms rather than the Campaign a broadcast channel would ordinarily take: 问题反馈 two rows
            // up already has that icon, and two identical megaphones on one screen tell nobody apart.
            AboutActionRow(
                title = stringResource(Res.string.about_app_channel),
                subtitle = stringResource(Res.string.about_app_channel_hint),
                icon = PlazaIcons.Sms,
                external = true,
                onClick = { onOpenUri(AppLinks.TELEGRAM_CHANNEL) },
            )
            AboutActionRow(
                title = stringResource(Res.string.about_app_group),
                subtitle = stringResource(Res.string.about_app_group_hint),
                icon = PlazaIcons.Group,
                external = true,
                onClick = { onOpenUri(AppLinks.TELEGRAM_GROUP) },
            )
            AboutActionRow(
                title = stringResource(Res.string.about_changelog),
                icon = PlazaIcons.History,
                onClick = onOpenChangelog,
            )
            AboutActionRow(
                title = stringResource(Res.string.settings_licenses),
                subtitle = stringResource(Res.string.about_licenses_hint),
                icon = PlazaIcons.Code,
                onClick = onOpenLicenses,
            )
            Text(
                stringResource(Res.string.about_theme_signature),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun AppIdentity(
    appName: String,
    versionName: String,
    versionCode: Long,
    check: UpdateCheck,
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
                Text("N", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.about_version, versionName, versionCode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(
                onClick = onCheckUpdates,
                enabled = check !is UpdateCheck.Checking,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                if (check is UpdateCheck.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(PlazaIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(Res.string.about_check_updates))
            }
            val highlighted = check is UpdateCheck.Available
            Text(
                checkSummary(check),
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** The one-line verdict beside the button. The card below carries everything actionable. */
@Composable
private fun checkSummary(check: UpdateCheck): String =
    when (check) {
        UpdateCheck.Idle -> stringResource(Res.string.about_update_unknown)
        UpdateCheck.Checking -> stringResource(Res.string.about_update_checking)
        UpdateCheck.UpToDate -> stringResource(Res.string.about_update_latest)
        is UpdateCheck.Available -> stringResource(Res.string.about_update_available, check.release.versionName)
        is UpdateCheck.Failed -> failureText(check.failure)
    }

/** Shared with [ChangelogScreen]: both screens fail against the same API, and say so the same way. */
@Composable
internal fun failureText(failure: UpdateFailure): String =
    when (failure) {
        UpdateFailure.Network -> stringResource(Res.string.about_update_failed_network)
        is UpdateFailure.Server -> stringResource(Res.string.about_update_failed_server, failure.statusCode)
        UpdateFailure.Checksum -> stringResource(Res.string.about_update_failed_checksum)
        UpdateFailure.Unreadable -> stringResource(Res.string.about_update_failed_unreadable)
        UpdateFailure.Storage -> stringResource(Res.string.about_update_failed_storage)
    }

@Composable
private fun installFailureText(failure: InstallFailure): String =
    when (failure) {
        InstallFailure.BLOCKED -> stringResource(Res.string.about_install_failed_blocked)
        InstallFailure.CONFLICT -> stringResource(Res.string.about_install_failed_conflict)
        InstallFailure.INCOMPATIBLE -> stringResource(Res.string.about_install_failed_incompatible)
        InstallFailure.STORAGE -> stringResource(Res.string.about_install_failed_storage)
        InstallFailure.INVALID -> stringResource(Res.string.about_install_failed_invalid)
        InstallFailure.UNKNOWN -> stringResource(Res.string.about_install_failed_unknown)
    }

/**
 * The whole of 应用内更新 as one block: what is new, how big it is, and the one button that fetches
 * and installs it.
 *
 * Only drawn when there *is* a newer release, so it never occupies the screen to say "nothing to do".
 */
@Composable
private fun UpdateCard(
    release: AppRelease,
    download: UpdateDownload,
    installFailure: InstallFailure?,
    needsInstallPermission: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onGrantInstallPermission: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(Res.string.about_update_new_version, release.versionName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Only ever set when 接收 dev 版更新 is on, and said out loud there: the card otherwise
                // looks exactly like the one offering a release that was actually tested.
                if (release.preRelease) {
                    TonalTag(
                        text = stringResource(Res.string.about_update_prerelease),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                if (release.sizeBytes > 0L) {
                    Text(
                        rememberFileSizeLabel(release.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (release.preRelease) {
                Text(
                    stringResource(Res.string.about_update_prerelease_hint),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            val notes = remember(release.notes) { releaseNotesText(release.notes) }
            if (notes.isNotBlank()) {
                Text(
                    notes,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = UPDATE_NOTES_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when (download) {
                is UpdateDownload.Running -> DownloadProgress(download, onCancelDownload)

                is UpdateDownload.Ready ->
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.about_update_install))
                    }

                else -> {
                    if (download is UpdateDownload.Failed) {
                        Text(
                            failureText(download.failure),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(PlazaIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            stringResource(
                                if (download is UpdateDownload.Failed) {
                                    Res.string.about_update_retry
                                } else {
                                    Res.string.about_update_download
                                },
                            ),
                        )
                    }
                }
            }

            if (needsInstallPermission) {
                InstallPermissionNotice(onGrantInstallPermission)
            }
            installFailure?.let {
                Text(
                    installFailureText(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(onClick = onOpenRelease, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.about_update_open_release))
            }
        }
    }
}

@Composable
private fun DownloadProgress(
    download: UpdateDownload.Running,
    onCancel: () -> Unit,
) {
    val fraction = download.fraction
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (fraction == null) {
                    stringResource(Res.string.about_update_downloading_unknown)
                } else {
                    stringResource(Res.string.about_update_downloading, (fraction * 100).roundToInt())
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.about_update_cancel))
            }
        }
    }
}

/**
 * Sideloading's one unavoidable manual step.
 *
 * Android grants `REQUEST_INSTALL_PACKAGES` per app, in Settings, by hand. Nothing the app does can
 * shorten that — it can only say which switch and why before opening the screen that holds it.
 */
@Composable
private fun InstallPermissionNotice(onGrant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(Res.string.about_update_permission_hint),
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(onClick = onGrant) {
            Text(stringResource(Res.string.about_update_permission_action))
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
                stringResource(Res.string.about_unofficial_notice),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Enough for the headline changes; the release page carries the rest. */
private const val UPDATE_NOTES_MAX_LINES = 8

internal object AppLinks {
    const val PROJECT_HOME = "https://github.com/5151561/nodyssey"
    const val ISSUES = "https://github.com/5151561/nodyssey/issues"
    const val RELEASES = "https://github.com/5151561/nodyssey/releases"

    /**
     * The project's own Telegram channel — where `release.yml` posts every build.
     *
     * One-way, so it is the row for people who only want to hear that a version exists; [TELEGRAM_GROUP]
     * is the one where anything is discussed back.
     */
    const val TELEGRAM_CHANNEL = "https://t.me/nodyssey_official"

    /**
     * The project's own Telegram group — Nodyssey's, not the forum's.
     *
     * NodeSeek's channel and group are on 关于 · 社区 under [CommunityLinks]; this one is where the app
     * itself is discussed, so it belongs beside 项目主页 and 问题反馈 instead. The invite link is the
     * published form of it: the group has no public username to link by name.
     *
     * Deliberately *not* the invite link the README publishes, and the two must not be merged: Telegram
     * counts joins per invite link, and keeping this one to itself is what makes "came in from the app"
     * a number that can be read at all.
     */
    const val TELEGRAM_GROUP = "https://t.me/+0mY1RaPADJMwNTdl"
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "f1 关于 Nodyssey")
@Composable
private fun AboutAppPreview() {
    PlazaTheme {
        AboutAppScreen(
            state = AboutAppUiState(versionName = "1.0.0", versionCode = 100),
            onBack = {},
            onCheckUpdates = {},
            onDownloadUpdate = {},
            onCancelDownload = {},
            onInstallUpdate = {},
            onGrantInstallPermission = {},
            onOpenChangelog = {},
            onOpenLicenses = {},
            onOpenUri = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "f1 关于 Nodyssey · 有新版本")
@Composable
private fun AboutAppUpdatePreview() {
    PlazaTheme {
        AboutAppScreen(
            state =
            AboutAppUiState(
                versionName = "1.1.0",
                versionCode = 3,
                update =
                AppUpdateState(
                    check =
                    UpdateCheck.Available(
                        AppRelease(
                            versionName = "1.2.0",
                            tag = "v1.2.0",
                            notes = "### 新增\n- 应用内更新\n\n### 修复\n- 跳页现在真的跳到那一页",
                            downloadUrl = "https://example.invalid/nodyssey-v1.2.0.apk",
                            assetName = "nodyssey-v1.2.0.apk",
                            sizeBytes = 8_800_000,
                            htmlUrl = AppLinks.RELEASES,
                        ),
                    ),
                ),
            ),
            onBack = {},
            onCheckUpdates = {},
            onDownloadUpdate = {},
            onCancelDownload = {},
            onInstallUpdate = {},
            onGrantInstallPermission = {},
            onOpenChangelog = {},
            onOpenLicenses = {},
            onOpenUri = {},
        )
    }
}
