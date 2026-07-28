package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_licenses)) },
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
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.licenses_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsSectionTitle(stringResource(R.string.licenses_app_section))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.app_name),
                    subtitle = "GNU General Public License v3.0",
                    top = true,
                    bottom = true,
                    onClick = { onOpenUri(APP_LICENSE_URL) },
                    trailing = { LicenseLinkIcon() },
                )
            }
            SettingsSectionTitle(stringResource(R.string.licenses_dependencies_section))
            SettingsGroup {
                SHIPPED_LIBRARIES.forEachIndexed { index, library ->
                    SettingsRow(
                        title = library.name,
                        subtitle = library.license,
                        top = index == 0,
                        bottom = index == SHIPPED_LIBRARIES.lastIndex,
                        onClick = { onOpenUri(library.sourceUrl) },
                        trailing = { LicenseLinkIcon() },
                    )
                }
            }
            Text(
                text = stringResource(R.string.licenses_lockfile_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun LicenseLinkIcon() {
    Icon(
        NodysseyIcons.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.md),
    )
}

private data class LibraryLicense(
    val name: String,
    val license: String,
    val sourceUrl: String,
)

private val SHIPPED_LIBRARIES =
    listOf(
        LibraryLicense("AndroidX · Jetpack Compose", "Apache License 2.0", "https://android.googlesource.com/platform/frameworks/support"),
        LibraryLicense("Kotlin · Coroutines · Serialization", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
        LibraryLicense("OkHttp", "Apache License 2.0", "https://github.com/square/okhttp"),
        LibraryLicense("Coil", "Apache License 2.0", "https://github.com/coil-kt/coil"),
        LibraryLicense("jsoup", "MIT License", "https://github.com/jhy/jsoup"),
    )

private const val APP_LICENSE_URL = "https://github.com/5151561/nodyssey/blob/main/LICENSE"

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun OpenSourceLicensesPreview() {
    NodysseyTheme { OpenSourceLicensesScreen(onBack = {}, onOpenUri = {}) }
}
