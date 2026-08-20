package io.github.nodyssey.ui.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.settings_dynamic_color
import io.github.nodyssey.ui.resources.settings_wallpaper_auto_update
import io.github.nodyssey.ui.resources.settings_wallpaper_auto_update_hint
import io.github.nodyssey.ui.resources.settings_wallpaper_candidate_label
import io.github.nodyssey.ui.resources.settings_wallpaper_candidates
import io.github.nodyssey.ui.resources.settings_wallpaper_candidates_hint
import io.github.nodyssey.ui.resources.settings_wallpaper_palette
import io.github.nodyssey.ui.resources.settings_wallpaper_system_palette
import io.github.nodyssey.ui.resources.settings_wallpaper_system_palette_hint
import io.github.nodyssey.ui.resources.settings_wallpaper_system_palette_unavailable
import io.github.nodyssey.ui.resources.settings_wallpaper_unreadable
import io.github.nodyssey.ui.resources.settings_wallpaper_unreadable_hint
import io.github.nodyssey.ui.settings.SettingsGroup
import io.github.nodyssey.ui.settings.SettingsRow
import io.github.nodyssey.ui.settings.SettingsSectionTitle
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun DynamicColorRoute(
    viewModel: ThemeSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DynamicColorScreen(
        settings = state.settings,
        onBack = onBack,
        onSeedSelected = viewModel::selectWallpaperSeed,
        onSystemPaletteChange = viewModel::setWallpaperSystemPalette,
        onAutoUpdateChange = viewModel::setWallpaperAutoUpdate,
        modifier = modifier,
    )
}

/**
 * 动态取色 — j1 卡2.
 *
 * j1 opens with a thumbnail of the wallpaper. It is not drawn: reading the wallpaper *image* has
 * needed `MANAGE_EXTERNAL_STORAGE` since API 33, and a screen that asks for all-files access to show
 * a picture the reader is already looking at on their home screen is not a trade worth making. The
 * colours themselves need no permission, so everything below the thumbnail is intact and the
 * candidates simply start at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicColorScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onSeedSelected: (Int) -> Unit,
    onSystemPaletteChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (retryKey, retry) = rememberRetryKey()
    val palette = rememberWallpaperPalette(retryKey)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val selected = settings.wallpaperSeed ?: palette.candidates.firstOrNull()?.toArgb()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(Res.string.settings_dynamic_color)) },
                scrollBehavior = scrollBehavior,
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
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (palette.candidates.isEmpty()) {
                WallpaperUnavailable(
                    fallback = Color(settings.seedColor),
                    onRetry = retry,
                )
            } else {
                SettingsSectionTitle(
                    stringResource(Res.string.settings_wallpaper_candidates, palette.candidates.size),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    palette.candidates.forEachIndexed { index, candidate ->
                        ThemeSwatch(
                            color = candidate,
                            selected = candidate.toArgb() == selected,
                            label =
                            stringResource(Res.string.settings_wallpaper_candidate_label, index + 1),
                            onClick = { onSeedSelected(candidate.toArgb()) },
                        )
                    }
                }
                Text(
                    stringResource(Res.string.settings_wallpaper_candidates_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )

                SettingsSectionTitle(stringResource(Res.string.settings_wallpaper_palette))
                SchemeStrip(
                    seed = Color(selected ?: settings.seedColor),
                    paletteStyle = settings.paletteStyle.toPlaza(),
                    darkTheme = LocalPlazaDarkTheme.current,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    corner = 12.dp,
                )
            }

            Box(Modifier.height(Spacing.xs))
            SettingsGroup {
                SettingsRow(
                    leading = { Icon(PlazaIcons.Android, contentDescription = null) },
                    title = stringResource(Res.string.settings_wallpaper_system_palette),
                    subtitle =
                    stringResource(
                        if (palette.systemPaletteAvailable) {
                            Res.string.settings_wallpaper_system_palette_hint
                        } else {
                            Res.string.settings_wallpaper_system_palette_unavailable
                        },
                        osVersionName(),
                    ),
                    checked = settings.wallpaperSystemPalette,
                    onCheckedChange = onSystemPaletteChange,
                    enabled = palette.systemPaletteAvailable,
                    top = true,
                    trailing = {
                        Switch(
                            checked = settings.wallpaperSystemPalette && palette.systemPaletteAvailable,
                            onCheckedChange = null,
                            enabled = palette.systemPaletteAvailable,
                        )
                    },
                )
                SettingsRow(
                    leading = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    title = stringResource(Res.string.settings_wallpaper_auto_update),
                    subtitle = stringResource(Res.string.settings_wallpaper_auto_update_hint),
                    checked = settings.wallpaperAutoUpdate,
                    onCheckedChange = onAutoUpdateChange,
                    bottom = true,
                    trailing = {
                        Switch(checked = settings.wallpaperAutoUpdate, onCheckedChange = null)
                    },
                )
            }
        }
    }
}

/**
 * The one thing that can go wrong here, and what the app did about it.
 *
 * It names the colour it fell back to rather than saying the theme is broken: nothing on screen has
 * changed, and a reader who does not know that will go looking for what did.
 */
@Composable
private fun WallpaperUnavailable(
    fallback: Color,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                PlazaIcons.ErrorCircle,
                contentDescription = null,
                modifier = Modifier.size(19.dp).align(Alignment.Top),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(Res.string.settings_wallpaper_unreadable),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(
                        Res.string.settings_wallpaper_unreadable_hint,
                        fallback.toHexString(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.action_retry))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DynamicColorPreview() {
    PlazaTheme {
        DynamicColorScreen(
            settings = UserSettings(),
            onBack = {},
            onSeedSelected = {},
            onSystemPaletteChange = {},
            onAutoUpdateChange = {},
        )
    }
}
