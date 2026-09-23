package io.github.nodyssey.ui.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.nodyssey.ui.resources.settings_wallpaper_ramp_neutral
import io.github.nodyssey.ui.resources.settings_wallpaper_ramp_primary
import io.github.nodyssey.ui.resources.settings_wallpaper_ramp_secondary
import io.github.nodyssey.ui.resources.settings_wallpaper_ramp_tertiary
import io.github.nodyssey.ui.resources.settings_wallpaper_system_palette
import io.github.nodyssey.ui.resources.settings_wallpaper_system_palette_hint
import io.github.nodyssey.ui.resources.settings_wallpaper_system_palette_unavailable
import io.github.nodyssey.ui.resources.settings_wallpaper_unreadable
import io.github.nodyssey.ui.resources.settings_wallpaper_unreadable_hint
import io.github.nodyssey.ui.settings.SettingsGroup
import io.github.nodyssey.ui.settings.SettingsItemGap
import io.github.nodyssey.ui.settings.SettingsPagePadding
import io.github.nodyssey.ui.settings.SettingsRow
import io.github.nodyssey.ui.settings.settingsRowTitleStyle
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaPaletteStyle
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.plazaSeedColorScheme
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
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
 * 动态取色 on a page of its own — j1 卡2.
 *
 * 主题 now shows the same controls inline under its 动态取色 segment (see [DynamicColorContent]), so
 * nothing in the app navigates here any more. The destination stays for one reason: a back stack
 * saved while it was on top still names it, and a restored stack that pointed at nothing would fail
 * to restore at all. It can go once no release that could have saved one is still in the field.
 *
 * j1 opens with a thumbnail of the wallpaper. It is not drawn: reading the wallpaper *image* has
 * needed `MANAGE_EXTERNAL_STORAGE` since API 33, and a screen that asks for all-files access to show
 * a picture the reader is already looking at on their home screen is not a trade worth making. The
 * colours themselves need no permission, so everything below the thumbnail is intact and the
 * candidates simply start at the top.
 */
@Composable
fun DynamicColorScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onSeedSelected: (Int) -> Unit,
    onSystemPaletteChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.settings_dynamic_color),
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
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(SettingsPagePadding),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
        ) {
            SettingsGroup {
                DynamicColorContent(
                    settings = settings,
                    onSeedSelected = onSeedSelected,
                    onSystemPaletteChange = onSystemPaletteChange,
                    onAutoUpdateChange = onAutoUpdateChange,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
        }
    }
}

/**
 * Everything 动态取色 has to say, drawn to sit inside a card: the wallpaper's candidates, the palette
 * the chosen one generates, and the two switches.
 *
 * The candidates and the ramps carry the card's 16dp inset themselves; the two switch rows bring
 * their own, being the same [SettingsRow]s every other card uses.
 */
@Composable
internal fun DynamicColorContent(
    settings: UserSettings,
    onSeedSelected: (Int) -> Unit,
    onSystemPaletteChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (retryKey, retry) = rememberRetryKey()
    val palette = rememberWallpaperPalette(retryKey)
    val selected = settings.wallpaperSeed ?: palette.candidates.firstOrNull()?.toArgb()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (palette.candidates.isEmpty()) {
                WallpaperUnavailable(
                    fallback = Color(settings.seedColor),
                    onRetry = retry,
                )
            } else {
                SectionLabel(
                    stringResource(Res.string.settings_wallpaper_candidates, palette.candidates.size),
                    color = MaterialTheme.colorScheme.onSurface,
                    contentPadding = PaddingValues(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    palette.candidates.forEachIndexed { index, candidate ->
                        ThemeSwatch(
                            color = candidate,
                            selected = candidate.toArgb() == selected,
                            label =
                            stringResource(Res.string.settings_wallpaper_candidate_label, index + 1),
                            onClick = { onSeedSelected(candidate.toArgb()) },
                            ringGround = LocalPlazaLayers.current.card,
                        )
                    }
                }
                Text(
                    stringResource(Res.string.settings_wallpaper_candidates_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionLabel(
                    stringResource(Res.string.settings_wallpaper_palette),
                    color = MaterialTheme.colorScheme.onSurface,
                    contentPadding = PaddingValues(),
                )
                SchemeRamps(
                    seed = Color(selected ?: settings.seedColor),
                    paletteStyle = settings.paletteStyle.toPlaza(),
                )
            }
        }

        Column {
            SettingsRow(
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
                    GroupedListItemSwitch(
                        checked = settings.wallpaperSystemPalette && palette.systemPaletteAvailable,
                        enabled = palette.systemPaletteAvailable,
                    )
                },
            )
            SettingsRow(
                title = stringResource(Res.string.settings_wallpaper_auto_update),
                subtitle = stringResource(Res.string.settings_wallpaper_auto_update_hint),
                checked = settings.wallpaperAutoUpdate,
                onCheckedChange = onAutoUpdateChange,
                top = true,
                bottom = true,
                trailing = { GroupedListItemSwitch(checked = settings.wallpaperAutoUpdate) },
            )
        }
    }
}

/**
 * 生成的调色板 — four families, five tones each, darkest first.
 *
 * The tones are read off a light scheme generated from the seed rather than from the scheme in force:
 * a ramp is the same whichever mode it is shown in, and the light scheme is the one whose roles
 * happen to land on five evenly spread tones per family — the `…Fixed` pair, the base role and the
 * two `on…Fixed` inks.
 */
@Composable
private fun SchemeRamps(
    seed: Color,
    paletteStyle: PlazaPaletteStyle,
    modifier: Modifier = Modifier,
) {
    val scheme = remember(seed, paletteStyle) { plazaSeedColorScheme(seed, false, paletteStyle) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RampRow(Res.string.settings_wallpaper_ramp_primary, scheme.primaryRamp())
        RampRow(Res.string.settings_wallpaper_ramp_secondary, scheme.secondaryRamp())
        RampRow(Res.string.settings_wallpaper_ramp_tertiary, scheme.tertiaryRamp())
        RampRow(Res.string.settings_wallpaper_ramp_neutral, scheme.neutralRamp())
    }
}

private fun ColorScheme.primaryRamp() =
    listOf(onPrimaryFixed, onPrimaryFixedVariant, primary, primaryFixedDim, primaryFixed)

private fun ColorScheme.secondaryRamp() =
    listOf(onSecondaryFixed, onSecondaryFixedVariant, secondary, secondaryFixedDim, secondaryFixed)

private fun ColorScheme.tertiaryRamp() =
    listOf(onTertiaryFixed, onTertiaryFixedVariant, tertiary, tertiaryFixedDim, tertiaryFixed)

private fun ColorScheme.neutralRamp() =
    listOf(onSurface, inverseSurface, outline, outlineVariant, surfaceContainerHigh)

@Composable
private fun RampRow(
    label: StringResource,
    tones: List<Color>,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(RampLabelWidth),
        )
        Row(Modifier.weight(1f).height(RampHeight).clip(RoundedCornerShape(8.dp))) {
            tones.forEach { tone ->
                Box(Modifier.weight(1f).fillMaxHeight().background(tone))
            }
        }
    }
}

private val RampLabelWidth = 54.dp
private val RampHeight = 24.dp

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
