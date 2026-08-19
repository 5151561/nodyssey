package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.plaza.core.update.ReleaseNote
import io.github.plaza.core.update.UpdateFailure
import io.github.plaza.core.update.releaseNotesText
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth

@Composable
fun ChangelogRoute(
    viewModel: ChangelogViewModel,
    onBack: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChangelogScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::refresh,
        onOpenReleases = onOpenReleases,
        onOpenRelease = onOpenUri,
        modifier = modifier,
    )
}

/**
 * 更新日志 — the published release notes, newest first.
 *
 * Every entry is one release: its version, the day it went out, and the notes as written, which are
 * that version's CHANGELOG section. The notes are flattened to text by [releaseNotesText] rather than
 * rendered as Markdown, the same as the update card does — the entries are short lines, and a
 * renderer would be a lot of machinery for a `- ` bullet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    state: ChangelogUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(R.string.about_changelog),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.changelog_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.failure != null ->
                    ChangelogError(
                        failure = state.failure,
                        onRetry = onRetry,
                        onOpenReleases = onOpenReleases,
                    )

                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().readableWidth(),
                        contentPadding = PaddingValues(Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        item {
                            Text(
                                stringResource(
                                    R.string.changelog_current_version,
                                    state.currentVersionName.ifBlank { "—" },
                                ),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        if (state.releases.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.changelog_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.releases, key = { it.tag }) { release ->
                            ReleaseEntry(
                                release = release,
                                installed = release.versionName == state.currentVersionName,
                                onOpen = { onOpenRelease(release.htmlUrl) },
                            )
                        }
                        item {
                            TextButton(onClick = onOpenReleases) {
                                Text(stringResource(R.string.changelog_all_releases))
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ReleaseEntry(
    release: ReleaseNote,
    installed: Boolean,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                release.tag,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (installed) {
                TonalTag(
                    text = stringResource(R.string.changelog_installed),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (release.preRelease) {
                TonalTag(
                    text = stringResource(R.string.about_update_prerelease),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                release.publishedOn,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        val notes = remember(release.notes) { releaseNotesText(release.notes) }
        Text(
            notes.ifBlank { stringResource(R.string.changelog_no_notes) },
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onOpen) {
            Text(stringResource(R.string.about_update_open_release))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ChangelogError(
    failure: UpdateFailure,
    onRetry: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().readableWidth().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(failureText(failure), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.changelog_failed_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_retry))
        }
        TextButton(onClick = onOpenReleases) {
            Text(stringResource(R.string.changelog_all_releases))
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "更新日志")
@Composable
private fun ChangelogPreview() {
    PlazaTheme {
        ChangelogScreen(
            state =
            ChangelogUiState(
                currentVersionName = "1.2.9",
                releases =
                listOf(
                    ReleaseNote(
                        versionName = "1.2.9",
                        tag = "v1.2.9",
                        notes = "### 新增\n- 可以自己选择要不要收 dev 版了。",
                        publishedOn = "2026-08-17",
                        preRelease = false,
                        htmlUrl = AppLinks.RELEASES,
                    ),
                    ReleaseNote(
                        versionName = "1.2.9-dev.1",
                        tag = "v1.2.9-dev.1",
                        notes = "Test build from tag `v1.2.9-dev.1`.",
                        publishedOn = "2026-08-15",
                        preRelease = true,
                        htmlUrl = AppLinks.RELEASES,
                    ),
                ),
            ),
            onBack = {},
            onRefresh = {},
            onRetry = {},
            onOpenReleases = {},
            onOpenRelease = {},
        )
    }
}
