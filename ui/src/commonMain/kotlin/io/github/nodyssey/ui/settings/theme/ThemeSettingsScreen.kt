package io.github.nodyssey.ui.settings.theme

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.PaletteStyle
import io.github.nodyssey.data.settings.SavedTheme
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.common.longPressToEdit
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_delete
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.action_new
import io.github.nodyssey.ui.resources.action_rename
import io.github.nodyssey.ui.resources.settings_color_source
import io.github.nodyssey.ui.resources.settings_color_source_custom
import io.github.nodyssey.ui.resources.settings_color_source_memory_hint
import io.github.nodyssey.ui.resources.settings_color_source_preset
import io.github.nodyssey.ui.resources.settings_color_source_wallpaper
import io.github.nodyssey.ui.resources.settings_color_source_wallpaper_value
import io.github.nodyssey.ui.resources.settings_my_themes
import io.github.nodyssey.ui.resources.settings_my_themes_hint
import io.github.nodyssey.ui.resources.settings_palette_style
import io.github.nodyssey.ui.resources.settings_palette_style_expressive
import io.github.nodyssey.ui.resources.settings_palette_style_monochrome
import io.github.nodyssey.ui.resources.settings_palette_style_neutral
import io.github.nodyssey.ui.resources.settings_palette_style_soft
import io.github.nodyssey.ui.resources.settings_palette_style_vibrant
import io.github.nodyssey.ui.resources.settings_presets
import io.github.nodyssey.ui.resources.settings_presets_hint
import io.github.nodyssey.ui.resources.settings_seed_name
import io.github.nodyssey.ui.resources.settings_theme
import io.github.nodyssey.ui.resources.settings_theme_preview
import io.github.nodyssey.ui.settings.SettingsSectionTitle
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

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
 * 主题 — j1 卡1 with j2's 预设, minus 明暗.
 *
 * The board opens with 跟随系统 / 浅色 / 深色 and this screen does not: that one control is reached far
 * more often than everything below it put together — it is what someone flips when the room changes,
 * not something they set once — and it stayed on 设置 where it has always been. Burying a daily
 * control two taps deep to keep a board intact is the wrong half of the design to honour.
 *
 * 预设 is the one section that comes and goes with the source: two rows of 56dp faces is most of
 * this page, and under 动态取色 or 自定义 none of them is the colour in force. Everything else stays
 * put — 我的主题 in particular, because 新建 is how a colour gets made in the first place and it would
 * be unreachable from the source it belongs to. The tiles carry each source's remembered value, so
 * collapsing the grid hides a control, never an answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onOpenDynamicColor: () -> Unit,
    onColorSourceChange: (ColorSource) -> Unit,
    onPresetSelected: (String) -> Unit,
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
                title = { Text(stringResource(Res.string.settings_theme)) },
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
            SettingsSectionTitle(stringResource(Res.string.settings_color_source))
            ColorSourceTiles(
                settings = settings,
                onSelect = onColorSourceChange,
                onOpenDynamicColor = onOpenDynamicColor,
                onOpenSeedSheet = { sheetOpen = true },
            )
            InfoLine(
                stringResource(
                    Res.string.settings_color_source_memory_hint,
                    Color(settings.seedColor).toHexString(),
                ),
            )

            // Two rows of swatches that cannot be the answer, sitting between the tiles and the rest
            // of the screen, is most of this page's height spent on a section the reader has already
            // navigated away from. The 预设 tile is what brings it back, and it still carries the
            // preset's name while the grid is away, so nothing about the choice is hidden — only the
            // six-way control for a choice that is not in force.
            AnimatedVisibility(visible = settings.colorSource == ColorSource.PRESET) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SettingsSectionTitle(stringResource(Res.string.settings_presets))
                    PresetGrid(selected = settings.presetId, onSelect = onPresetSelected)
                    InfoLine(stringResource(Res.string.settings_presets_hint))
                }
            }

            SettingsSectionTitle(stringResource(Res.string.settings_my_themes))
            SavedThemeChips(
                themes = settings.savedThemes,
                selected = settings.seedColor.takeIf { settings.colorSource == ColorSource.CUSTOM },
                onSelect = onCustomSeedSelected,
                onRename = { renaming = it },
                onDelete = onDeleteTheme,
                onCreate = { sheetOpen = true },
            )
            Text(
                stringResource(Res.string.settings_my_themes_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )

            SettingsSectionTitle(stringResource(Res.string.settings_palette_style))
            // 色彩风格 steers the generator, and a 角色预设 never reaches it. Greyed rather than
            // hidden: the chips are still the answer for the other five ways of getting a colour,
            // and a section that vanished would read as one the app had lost.
            PaletteStyleChips(
                selected = settings.paletteStyle,
                onSelect = onPaletteStyleChange,
                enabled = activeCharacterPalette(settings) == null,
            )

            SettingsSectionTitle(stringResource(Res.string.settings_theme_preview))
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
    val presetName = stringResource(presetById(settings.presetId).label)

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ColorSourceTile(
            icon = PlazaIcons.Palette,
            title = stringResource(Res.string.settings_color_source_preset),
            value = presetName,
            selected = settings.colorSource == ColorSource.PRESET,
            onClick = { onSelect(ColorSource.PRESET) },
            modifier = Modifier.weight(1f),
        )
        if (supportsWallpaperColorSource()) {
            ColorSourceTile(
                icon = PlazaIcons.Wallpaper,
                title = stringResource(Res.string.settings_color_source_wallpaper),
                value = stringResource(Res.string.settings_color_source_wallpaper_value),
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
            title = stringResource(Res.string.settings_color_source_custom),
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
    /** The grid is only on screen while 预设 is the source, so one of the six is always the answer. */
    selected: String,
    onSelect: (String) -> Unit,
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
                            selected = preset.id == selected,
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

/**
 * One face, its name, and the three colours it is made of.
 *
 * The line under the name is the character's own summary — 青×灰×粉 — rather than a hex, because a
 * hex names one colour and none of these presets is one colour. 石墨青 keeps the slot and says 默认.
 */
@Composable
private fun PresetCell(
    preset: ThemePreset,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(preset.id) },
            ).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PresetDot(
            avatar = preset.avatar,
            selected = selected,
            plate = MaterialTheme.colorScheme.surfaceContainer,
        )
        Text(
            stringResource(preset.label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(preset.subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The 说明 line j1 and j2 both put under a section: one small icon, one paragraph, no card. */
@Composable
private fun InfoLine(text: String) {
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
            text,
            style = MaterialTheme.typography.labelMedium,
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
                        text = { Text(stringResource(Res.string.action_rename)) },
                        onClick = {
                            menuFor = null
                            onRename(theme)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_delete)) },
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
            label = { Text(stringResource(Res.string.action_new)) },
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
    enabled: Boolean,
) {
    val labels =
        listOf(
            PaletteStyle.SOFT to Res.string.settings_palette_style_soft,
            PaletteStyle.VIBRANT to Res.string.settings_palette_style_vibrant,
            PaletteStyle.EXPRESSIVE to Res.string.settings_palette_style_expressive,
            PaletteStyle.NEUTRAL to Res.string.settings_palette_style_neutral,
            PaletteStyle.MONOCHROME to Res.string.settings_palette_style_monochrome,
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
                enabled = enabled,
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
        title = { Text(stringResource(Res.string.action_rename)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it.take(SAVED_THEME_NAME_LIMIT) },
                singleLine = true,
                label = { Text(stringResource(Res.string.settings_seed_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { Color(theme.color).toHexString() }) },
            ) {
                Text(stringResource(Res.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
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
