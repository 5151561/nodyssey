package io.github.nodyssey.ui.settings.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.PaletteStyle
import io.github.nodyssey.data.settings.SavedTheme
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.common.longPressToEdit
import io.github.nodyssey.ui.settings.SettingsSectionTitle
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth

@Composable
fun ThemeSettingsRoute(
    viewModel: ThemeSettingsViewModel,
    onBack: () -> Unit,
    onOpenDynamicColor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ThemeSettingsScreen(
        settings = state.settings,
        onBack = onBack,
        onOpenDynamicColor = onOpenDynamicColor,
        onColorSourceChange = viewModel::setColorSource,
        onPresetSelected = viewModel::selectPreset,
        onCustomSeedSelected = viewModel::selectCustomSeed,
        onPaletteStyleChange = viewModel::setPaletteStyle,
        onSaveTheme = viewModel::saveTheme,
        onDeleteTheme = viewModel::deleteTheme,
        modifier = modifier,
    )
}

/**
 * 主题 — j1 卡1, minus 明暗.
 *
 * The board opens with 跟随系统 / 浅色 / 深色 and this screen does not: that one control is reached far
 * more often than everything below it put together — it is what someone flips when the room changes,
 * not something they set once — and it stayed on 设置 where it has always been. Burying a daily
 * control two taps deep to keep a board intact is the wrong half of the design to honour.
 *
 * Every section stays on screen whichever source is selected, rather than the grid appearing and
 * disappearing under the tiles. Two reasons: the sources each remember their own seed, so the grid
 * is still showing a live answer while 自定义 is in force; and a settings screen that reflows as you
 * tap across it is one where the thing you were reaching for has moved by the time you get there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onOpenDynamicColor: () -> Unit,
    onColorSourceChange: (ColorSource) -> Unit,
    onPresetSelected: (Int) -> Unit,
    onCustomSeedSelected: (Int) -> Unit,
    onPaletteStyleChange: (PaletteStyle) -> Unit,
    onSaveTheme: (String, Int) -> Unit,
    onDeleteTheme: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedTheme?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.settings_theme)) },
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
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SettingsSectionTitle(stringResource(R.string.settings_color_source))
            ColorSourceTiles(
                settings = settings,
                onSelect = onColorSourceChange,
                onOpenDynamicColor = onOpenDynamicColor,
                onOpenSeedSheet = { sheetOpen = true },
            )
            Row(
                modifier = Modifier.padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp).padding(top = 1.dp),
                )
                Text(
                    stringResource(
                        R.string.settings_color_source_memory_hint,
                        Color(settings.seedColor).toHexString(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_presets))
            PresetGrid(
                selected = settings.presetSeed.takeIf { settings.colorSource == ColorSource.PRESET },
                onSelect = onPresetSelected,
            )

            SettingsSectionTitle(stringResource(R.string.settings_my_themes))
            SavedThemeChips(
                themes = settings.savedThemes,
                selected = settings.seedColor.takeIf { settings.colorSource == ColorSource.CUSTOM },
                onSelect = onCustomSeedSelected,
                onRename = { renaming = it },
                onDelete = onDeleteTheme,
                onCreate = { sheetOpen = true },
            )
            Text(
                stringResource(R.string.settings_my_themes_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )

            SettingsSectionTitle(stringResource(R.string.settings_palette_style))
            PaletteStyleChips(settings.paletteStyle, onPaletteStyleChange)

            SettingsSectionTitle(stringResource(R.string.settings_theme_preview))
            ThemePreviewCard()
        }
    }

    if (sheetOpen) {
        SeedColorSheet(
            initial = Color(settings.seedColor),
            paletteStyle = settings.paletteStyle.toPlaza(),
            onDismiss = { sheetOpen = false },
            onApply = { color, name ->
                onCustomSeedSelected(color.toArgb())
                name?.let { onSaveTheme(it, color.toArgb()) }
                sheetOpen = false
            },
        )
    }

    renaming?.let { theme ->
        RenameThemeDialog(
            theme = theme,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onSaveTheme(name, theme.color)
                renaming = null
            },
        )
    }
}

/**
 * 预设 / 动态取色 / 自定义, as three tiles rather than three segments.
 *
 * Each carries the value it would restore — the preset's name, "跟随壁纸", the hex a colour was left
 * on — which is what makes the info line under them true rather than a promise.
 *
 * A tile that is already selected opens what is behind it: 动态取色's own screen, 自定义's picker.
 * Selecting and opening are separate taps on purpose, because they answer different questions —
 * "put my old colour back" and "let me change it" — and j1 asks for both on the same tile.
 *
 * 动态取色 is dropped below API 31: there is no system palette to read there, and a tile that can
 * never be picked is worse than one that was never offered.
 */
@Composable
private fun ColorSourceTiles(
    settings: UserSettings,
    onSelect: (ColorSource) -> Unit,
    onOpenDynamicColor: () -> Unit,
    onOpenSeedSheet: () -> Unit,
) {
    val presetName =
        ThemePresets.firstOrNull { it.seed.toArgb() == settings.presetSeed }
            ?.let { stringResource(it.label) }
            ?: Color(settings.presetSeed).toHexString()

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ColorSourceTile(
            icon = PlazaIcons.Palette,
            title = stringResource(R.string.settings_color_source_preset),
            value = presetName,
            selected = settings.colorSource == ColorSource.PRESET,
            onClick = { onSelect(ColorSource.PRESET) },
            modifier = Modifier.weight(1f),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ColorSourceTile(
                icon = PlazaIcons.Wallpaper,
                title = stringResource(R.string.settings_color_source_wallpaper),
                value = stringResource(R.string.settings_color_source_wallpaper_value),
                selected = settings.colorSource == ColorSource.WALLPAPER,
                onClick = {
                    if (settings.colorSource == ColorSource.WALLPAPER) {
                        onOpenDynamicColor()
                    } else {
                        onSelect(ColorSource.WALLPAPER)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        ColorSourceTile(
            swatch = Color(settings.seedColor),
            title = stringResource(R.string.settings_color_source_custom),
            value = Color(settings.seedColor).toHexString(),
            selected = settings.colorSource == ColorSource.CUSTOM,
            onClick = {
                if (settings.colorSource == ColorSource.CUSTOM) {
                    onOpenSeedSheet()
                } else {
                    onSelect(ColorSource.CUSTOM)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColorSourceTile(
    title: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    swatch: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = TileHeight).selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            when {
                icon != null ->
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )

                swatch != null ->
                    Box(Modifier.size(22.dp).clip(CircleShape).background(swatch))
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The six presets on one plate: three columns, two rows.
 *
 * Rows rather than a `LazyVerticalGrid`, because the grid would be the second scrolling container
 * inside the screen's own scroll — which either measures to zero height or eats the drag that was
 * meant for the page.
 */
@Composable
private fun PresetGrid(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 6.dp)) {
            ThemePresets.chunked(PRESET_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { preset ->
                        PresetCell(
                            preset = preset,
                            selected = preset.seed.toArgb() == selected,
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps a short last row's cells on the same columns as the row above it.
                    repeat(PRESET_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PresetCell(
    preset: ThemePreset,
    selected: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(preset.label)
    Column(
        modifier =
        modifier
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(preset.seed.toArgb()) },
            ).padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ThemeSwatch(
            color = preset.seed,
            second = preset.companion,
            selected = selected,
            label = label,
            size = PresetSwatchSize,
            ringGround = MaterialTheme.colorScheme.surfaceContainer,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Text(
            preset.seed.toHexString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 我的主题 — the seeds a reader named, plus the way to make another.
 *
 * Long-press opens rename and delete, which is what j1's line under the row says. There is no
 * swipe and no edit mode: the row is at most a dozen chips, and both actions are rare.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedThemeChips(
    themes: List<SavedTheme>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onRename: (SavedTheme) -> Unit,
    onDelete: (Int) -> Unit,
    onCreate: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<SavedTheme?>(null) }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        themes.forEach { theme ->
            Box {
                AssistChip(
                    onClick = { onSelect(theme.color) },
                    label = { Text(theme.name.ifBlank { Color(theme.color).toHexString() }) },
                    leadingIcon = {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(theme.color)))
                    },
                    colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor =
                        if (theme.color == selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ),
                    border =
                    AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor =
                        if (theme.color == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    modifier = Modifier.longPressToEdit { menuFor = theme },
                )
                DropdownMenu(
                    expanded = menuFor == theme,
                    onDismissRequest = { menuFor = null },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            menuFor = null
                            onRename(theme)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = {
                            menuFor = null
                            onDelete(theme.color)
                        },
                    )
                }
            }
        }
        AssistChip(
            onClick = onCreate,
            label = { Text(stringResource(R.string.action_new)) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            colors =
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            border = null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteStyleChips(
    selected: PaletteStyle,
    onSelect: (PaletteStyle) -> Unit,
) {
    val labels =
        listOf(
            PaletteStyle.SOFT to R.string.settings_palette_style_soft,
            PaletteStyle.VIBRANT to R.string.settings_palette_style_vibrant,
            PaletteStyle.EXPRESSIVE to R.string.settings_palette_style_expressive,
            PaletteStyle.NEUTRAL to R.string.settings_palette_style_neutral,
            PaletteStyle.MONOCHROME to R.string.settings_palette_style_monochrome,
        )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { (style, label) ->
            FilterChip(
                selected = style == selected,
                onClick = { onSelect(style) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun RenameThemeDialog(
    theme: SavedTheme,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(theme) { mutableStateOf(theme.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it.take(SAVED_THEME_NAME_LIMIT) },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_seed_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { Color(theme.color).toHexString() }) },
            ) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val PRESET_COLUMNS = 3
private const val SAVED_THEME_NAME_LIMIT = 16
private val TileHeight = 96.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun ThemeSettingsPreview() {
    PlazaTheme {
        ThemeSettingsScreen(
            settings =
            UserSettings(
                savedThemes =
                listOf(
                    SavedTheme("海雾", 0xFF2F6D8C.toInt()),
                    SavedTheme("夜樱", 0xFF8A4A66.toInt()),
                ),
                seedColor = SettingsRepository.DEFAULT_SEED_COLOR,
            ),
            onBack = {},
            onOpenDynamicColor = {},
            onColorSourceChange = {},
            onPresetSelected = {},
            onCustomSeedSelected = {},
            onPaletteStyleChange = {},
            onSaveTheme = { _, _ -> },
            onDeleteTheme = {},
        )
    }
}
