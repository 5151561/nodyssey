package io.github.nodyssey.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.update.releaseNotesText
import io.github.nodyssey.data.update.AppRelease
import io.github.nodyssey.data.update.AppUpdateState
import io.github.nodyssey.data.update.InstallFailure
import io.github.nodyssey.data.update.UpdateCheck
import io.github.nodyssey.data.update.UpdateDownload
import io.github.nodyssey.data.update.UpdateFailure
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth
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
    // Whatever the user did on the settings screen, the answer is the same question asked again —
    // `canRequestPackageInstalls` is what decides, not the result code, which this contract does not
    // meaningfully carry for a settings toggle.
    val installPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onInstallPermissionResult()
        }

    AboutAppScreen(
        state = state,
        onBack = onBack,
        onCheckUpdates = viewModel::checkForUpdates,
        onDownloadUpdate = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onInstallUpdate = viewModel::install,
        onGrantInstallPermission = {
            runCatching { installPermission.launch(viewModel.installPermissionIntent()) }
        },
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.about_title),
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
            AppIdentity(
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
                title = stringResource(R.string.about_project_home),
                subtitle = "github.com/5151561/nodyssey",
                icon = NodysseyIcons.Code,
                external = true,
                onClick = { onOpenUri(AppLinks.PROJECT_HOME) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_feedback),
                subtitle = stringResource(R.string.about_feedback_hint),
                icon = NodysseyIcons.Campaign,
                external = true,
                onClick = { onOpenUri(AppLinks.ISSUES) },
            )
            AboutActionRow(
                title = stringResource(R.string.about_changelog),
                icon = NodysseyIcons.History,
                onClick = onOpenChangelog,
            )
            AboutActionRow(
                title = stringResource(R.string.settings_licenses),
                subtitle = stringResource(R.string.about_licenses_hint),
                icon = NodysseyIcons.Code,
                onClick = onOpenLicenses,
            )
            Text(
                stringResource(R.string.about_theme_signature),
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
                    Icon(NodysseyIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.about_check_updates))
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
        UpdateCheck.Idle -> stringResource(R.string.about_update_unknown)
        UpdateCheck.Checking -> stringResource(R.string.about_update_checking)
        UpdateCheck.UpToDate -> stringResource(R.string.about_update_latest)
        is UpdateCheck.Available -> stringResource(R.string.about_update_available, check.release.versionName)
        is UpdateCheck.Failed -> failureText(check.failure)
    }

@Composable
private fun failureText(failure: UpdateFailure): String =
    when (failure) {
        UpdateFailure.Network -> stringResource(R.string.about_update_failed_network)
        is UpdateFailure.Server -> stringResource(R.string.about_update_failed_server, failure.statusCode)
        UpdateFailure.Unreadable -> stringResource(R.string.about_update_failed_unreadable)
        UpdateFailure.Storage -> stringResource(R.string.about_update_failed_storage)
    }

@Composable
private fun installFailureText(failure: InstallFailure): String =
    when (failure) {
        InstallFailure.BLOCKED -> stringResource(R.string.about_install_failed_blocked)
        InstallFailure.CONFLICT -> stringResource(R.string.about_install_failed_conflict)
        InstallFailure.INCOMPATIBLE -> stringResource(R.string.about_install_failed_incompatible)
        InstallFailure.STORAGE -> stringResource(R.string.about_install_failed_storage)
        InstallFailure.INVALID -> stringResource(R.string.about_install_failed_invalid)
        InstallFailure.UNKNOWN -> stringResource(R.string.about_install_failed_unknown)
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
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.about_update_new_version, release.versionName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (release.sizeBytes > 0L) {
                    Text(
                        Formatter.formatShortFileSize(context, release.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
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
                        Text(stringResource(R.string.about_update_install))
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
                        Icon(NodysseyIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            stringResource(
                                if (download is UpdateDownload.Failed) {
                                    R.string.about_update_retry
                                } else {
                                    R.string.about_update_download
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
                Text(stringResource(R.string.about_update_open_release))
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
                    stringResource(R.string.about_update_downloading_unknown)
                } else {
                    stringResource(R.string.about_update_downloading, (fraction * 100).roundToInt())
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.about_update_cancel))
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
            stringResource(R.string.about_update_permission_hint),
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(onClick = onGrant) {
            Text(stringResource(R.string.about_update_permission_action))
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

/** Enough for the headline changes; the release page carries the rest. */
private const val UPDATE_NOTES_MAX_LINES = 8

internal object AppLinks {
    const val PROJECT_HOME = "https://github.com/5151561/nodyssey"
    const val ISSUES = "https://github.com/5151561/nodyssey/issues"
    const val RELEASES = "https://github.com/5151561/nodyssey/releases"
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "f1 关于 Nodyssey")
@Composable
private fun AboutAppPreview() {
    NodysseyTheme {
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
    NodysseyTheme {
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
