package io.github.nodyssey.ui.settings.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import io.github.nodyssey.ui.resources.settings_color_source_preset
import io.github.nodyssey.ui.resources.settings_color_source_wallpaper
import io.github.nodyssey.ui.resources.settings_my_themes
import io.github.nodyssey.ui.resources.settings_my_themes_hint
import io.github.nodyssey.ui.resources.settings_palette_style
import io.github.nodyssey.ui.resources.settings_seed_edit
import io.github.nodyssey.ui.resources.settings_seed_name
import io.github.nodyssey.ui.resources.settings_theme
import io.github.nodyssey.ui.resources.settings_theme_preview
import io.github.nodyssey.ui.settings.DISABLED_ALPHA
import io.github.nodyssey.ui.settings.SettingsGroup
import io.github.nodyssey.ui.settings.SettingsIcons
import io.github.nodyssey.ui.settings.SettingsItemGap
import io.github.nodyssey.ui.settings.SettingsPagePadding
import io.github.nodyssey.ui.settings.SettingsRow
import io.github.nodyssey.ui.settings.settingsRowTitleStyle
import io.github.plaza.designsys.component.ChoiceSegments
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun ThemeSettingsRoute(
    viewModel: ThemeSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ThemeSettingsScreen(
        settings = state.settings,
        onBack = onBack,
        onColorSourceChange = viewModel::setColorSource,
        onPresetSelected = viewModel::selectPreset,
        onCustomSeedSelected = viewModel::selectCustomSeed,
        onPaletteStyleChange = viewModel::setPaletteStyle,
        onSaveTheme = viewModel::saveTheme,
        onDeleteTheme = viewModel::deleteTheme,
        onWallpaperSeedSelected = viewModel::selectWallpaperSeed,
        onWallpaperSystemPaletteChange = viewModel::setWallpaperSystemPalette,
        onWallpaperAutoUpdateChange = viewModel::setWallpaperAutoUpdate,
        modifier = modifier,
    )
}

/**
 * 主题 — two cards and a preview: where the colour comes from, then what is kept and how it is
 * spread.
 *
 * The first card is 配色来源 and whatever that source has to offer, under one segmented control: the
 * six presets, the wallpaper's candidates and palette, or the custom seed with its 编辑. Only the
 * source in force is drawn — two rows of faces under 自定义 would be most of the page spent on a
 * choice that is not the answer — and each source keeps its own stored value (preset, wallpaper
 * seed, custom seed), so switching back restores what was there rather than starting over.
 *
 * 明暗 is not here. That one control is reached far more often than everything below put together —
 * it is what someone flips when the room changes, not something they set once — and it stayed on
 * 设置 where it has always been.
 */
@Composable
fun ThemeSettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onColorSourceChange: (ColorSource) -> Unit,
    onPresetSelected: (String) -> Unit,
    onCustomSeedSelected: (Int) -> Unit,
    onPaletteStyleChange: (PaletteStyle) -> Unit,
    onSaveTheme: (String, Int) -> Unit,
    onDeleteTheme: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onWallpaperSeedSelected: (Int) -> Unit = {},
    onWallpaperSystemPaletteChange: (Boolean) -> Unit = {},
    onWallpaperAutoUpdateChange: (Boolean) -> Unit = {},
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedTheme?>(null) }
    val appBarState = rememberOneHandAppBarState()

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.settings_theme),
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
                ColorSourceHeader(settings = settings, onSelect = onColorSourceChange)
                when (settings.colorSource) {
                    ColorSource.PRESET ->
                        PresetGrid(
                            selected = settings.presetId,
                            onSelect = onPresetSelected,
                            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.sm),
                        )

                    ColorSource.WALLPAPER ->
                        DynamicColorContent(
                            settings = settings,
                            onSeedSelected = onWallpaperSeedSelected,
                            onSystemPaletteChange = onWallpaperSystemPaletteChange,
                            onAutoUpdateChange = onWallpaperAutoUpdateChange,
                        )

                    ColorSource.CUSTOM ->
                        CustomSeedRow(
                            settings = settings,
                            onEdit = { sheetOpen = true },
                        )
                }
            }

            SettingsGroup {
                MyThemesBlock(
                    themes = settings.savedThemes,
                    selected = settings.seedColor.takeIf { settings.colorSource == ColorSource.CUSTOM },
                    onSelect = onCustomSeedSelected,
                    onRename = { renaming = it },
                    onDelete = onDeleteTheme,
                    onCreate = { sheetOpen = true },
                )
                // 色彩风格 steers the generator, and a 角色预设 never reaches it. Greyed rather than
                // hidden: it is still the answer for every other way of getting a colour, and a row
                // that vanished would read as one the app had lost.
                PaletteStyleRow(
                    selected = settings.paletteStyle,
                    onSelect = onPaletteStyleChange,
                    enabled = activeCharacterPalette(settings) == null,
                )
            }

            SectionLabel(stringResource(Res.string.settings_theme_preview))
            ThemePreviewCard()
        }
    }

    if (sheetOpen) {
        SeedColorSheet(
            initial = Color(settings.seedColor),
            paletteStyle = settings.paletteStyle.toPlaza(),
            initialName =
            settings.savedThemes.firstOrNull { it.color == settings.seedColor }?.name.orEmpty(),
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
 * 配色来源 and the segmented 预设 / 动态取色 / 自定义 under it.
 *
 * 动态取色 is dropped below API 31: there is no system palette to read there, and a segment that can
 * never be picked is worse than one that was never offered.
 */
@Composable
private fun ColorSourceHeader(
    settings: UserSettings,
    onSelect: (ColorSource) -> Unit,
) {
    val choices =
        buildList {
            add(ColorSource.PRESET to stringResource(Res.string.settings_color_source_preset))
            if (supportsWallpaperColorSource()) {
                add(ColorSource.WALLPAPER to stringResource(Res.string.settings_color_source_wallpaper))
            }
            add(ColorSource.CUSTOM to stringResource(Res.string.settings_color_source_custom))
        }
    Column(
        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = 14.dp, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.settings_color_source), style = settingsRowTitleStyle())
        ChoiceSegments(
            labels = choices.map { it.second },
            selectedIndex = choices.indexOfFirst { it.first == settings.colorSource },
            onSelect = { onSelect(choices[it].first) },
        )
    }
}

/**
 * The six presets: three columns, two rows.
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
    Column(modifier = modifier.fillMaxWidth()) {
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

/**
 * One face and what it is made of — 青×灰×粉 rather than a hex, because a hex names one colour and
 * none of these presets is one colour. 石墨青, the seed, is the one that goes by a name.
 *
 * One line, so the six cells are the same height and the grid closes up under the dots.
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
            plate = LocalPlazaLayers.current.card,
        )
        Text(
            stringResource(preset.label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 自定义's answer: the seed in force, named the way 我的主题 names it, and 编辑 to change it.
 *
 * 编辑 is its own button rather than a second tap on the segment, which is how the tile this replaced
 * worked: selecting a source and changing it answer different questions — "put my colour back" and
 * "let me pick another" — and a segmented control that opened a sheet on its second press would be
 * the one segmented control in the app that did.
 */
@Composable
private fun CustomSeedRow(
    settings: UserSettings,
    onEdit: () -> Unit,
) {
    val seed = Color(settings.seedColor)
    val name = settings.savedThemes.firstOrNull { it.color == settings.seedColor }?.name?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(seed))
        Column(Modifier.weight(1f)) {
            Text(name ?: seed.toHexString(), style = settingsRowTitleStyle())
            if (name != null) {
                Text(
                    seed.toHexString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledTonalButton(onClick = onEdit) {
            Text(stringResource(Res.string.settings_seed_edit))
        }
    }
}

/**
 * 我的主题 — the seeds a reader named, plus the way to make another.
 *
 * Long-press opens rename and delete, which is what the line beside the heading says. There is no
 * swipe and no edit mode: the row is at most a dozen chips, and both actions are rare.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyThemesBlock(
    themes: List<SavedTheme>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onRename: (SavedTheme) -> Unit,
    onDelete: (Int) -> Unit,
    onCreate: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<SavedTheme?>(null) }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = 14.dp, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.settings_my_themes),
                style = settingsRowTitleStyle(),
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(Res.string.settings_my_themes_hint),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            themes.forEach { theme ->
                val isSelected = theme.color == selected
                Box {
                    AssistChip(
                        onClick = { onSelect(theme.color) },
                        label = { Text(theme.name.ifBlank { Color(theme.color).toHexString() }) },
                        leadingIcon = {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(theme.color)))
                        },
                        shape = ChipShape,
                        colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor =
                            if (isSelected) scheme.secondaryContainer else LocalPlazaLayers.current.card,
                            labelColor = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface,
                        ),
                        border = BorderStroke(1.dp, if (isSelected) scheme.secondaryContainer else scheme.outline),
                        modifier = Modifier.height(ChipHeight).longPressToEdit { menuFor = theme },
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
                shape = ChipShape,
                colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = LocalPlazaLayers.current.card,
                    labelColor = scheme.primary,
                ),
                border = BorderStroke(1.dp, scheme.outline),
                modifier = Modifier.height(ChipHeight),
            )
        }
    }
}

/**
 * 色彩风格 as one row with its answer on the second line and the five choices behind a menu.
 *
 * Five chips took a section of their own for a setting most readers leave on 柔和; folded into a row
 * it sits in the same card as 我的主题, which is the other thing that only matters to a seed.
 */
@Composable
private fun PaletteStyleRow(
    selected: PaletteStyle,
    onSelect: (PaletteStyle) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsRow(
        modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
        leading = { Icon(SettingsIcons.Tonality, contentDescription = null) },
        title = stringResource(Res.string.settings_palette_style),
        subtitle = stringResource(paletteStyleLabel(selected)),
        bottom = true,
        enabled = enabled,
        onClick = { expanded = true },
        trailing = {
            // Anchored to the arrow for the same reason 语言's menu is on 设置: a `DropdownMenu` hangs
            // off its parent layout node, and the parent here is only the arrow.
            Box {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    PaletteStyleChoices.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(stringResource(paletteStyleLabel(style))) },
                            onClick = {
                                expanded = false
                                onSelect(style)
                            },
                            trailingIcon = {
                                if (style == selected) Icon(Icons.Default.Check, contentDescription = null)
                            },
                        )
                    }
                }
            }
        },
    )
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
private val ChipShape = RoundedCornerShape(12.dp)
private val ChipHeight = 36.dp

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
            onColorSourceChange = {},
            onPresetSelected = {},
            onCustomSeedSelected = {},
            onPaletteStyleChange = {},
            onSaveTheme = { _, _ -> },
            onDeleteTheme = {},
        )
    }
}
