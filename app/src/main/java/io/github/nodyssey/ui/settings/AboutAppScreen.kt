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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth

sealed interface AppUpdateStatus {
    data object Unknown : AppUpdateStatus

    data object Latest : AppUpdateStatus

    data class Available(val version: String) : AppUpdateStatus
}

/** 本软件的关于页；站点与社区信息由 [AboutCommunityScreen] 独立承载。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppScreen(
    versionName: String,
    versionCode: Long,
    updateStatus: AppUpdateStatus,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
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
                versionName = versionName,
                versionCode = versionCode,
                updateStatus = updateStatus,
                onCheckUpdates = onCheckUpdates,
            )
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
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(NodysseyIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
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
            versionName = "1.0.0",
            versionCode = 100,
            updateStatus = AppUpdateStatus.Latest,
            onBack = {},
            onCheckUpdates = {},
            onOpenChangelog = {},
            onOpenLicenses = {},
            onOpenUri = {},
        )
    }
}
