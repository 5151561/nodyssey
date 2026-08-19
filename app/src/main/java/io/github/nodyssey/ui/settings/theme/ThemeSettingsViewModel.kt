package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.PaletteStyle
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.di.AppContainer
import io.github.plaza.designsys.theme.PlazaPaletteStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主题 and its 动态取色 child, off one view model.
 *
 * Both screens edit the same six fields and neither holds anything the other does not; splitting
 * them would have meant two collectors on the same store and two chances for the seed the reader is
 * looking at to disagree with the one the app is drawing.
 */
class ThemeSettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<ThemeSettingsUiState> =
        settings.settings
            .map(::ThemeSettingsUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeSettingsUiState(),
            )

    fun setColorSource(value: ColorSource) {
        viewModelScope.launch { settings.setColorSource(value) }
    }

    /** Picking a preset is also picking 预设 — the grid is the section under that tile. */
    fun selectPreset(argb: Int) {
        viewModelScope.launch {
            settings.setPresetSeed(argb)
            settings.setColorSource(ColorSource.PRESET)
        }
    }

    /** Same for a saved theme and 自定义: a chip nobody could select would be decoration. */
    fun selectCustomSeed(argb: Int) {
        viewModelScope.launch {
            settings.setSeedColor(argb)
            settings.setColorSource(ColorSource.CUSTOM)
        }
    }

    fun selectWallpaperSeed(argb: Int) {
        viewModelScope.launch {
            settings.setWallpaperSeed(argb)
            // Picking a candidate by hand is the answer to 使用系统调色板 as well: the system's own
            // palette is built from a seed this phone chose, and a reader who just chose a different
            // one would otherwise watch the tap do nothing.
            settings.setWallpaperSystemPalette(false)
            settings.setColorSource(ColorSource.WALLPAPER)
        }
    }

    fun setWallpaperSystemPalette(value: Boolean) {
        viewModelScope.launch { settings.setWallpaperSystemPalette(value) }
    }

    fun setWallpaperAutoUpdate(value: Boolean) {
        viewModelScope.launch { settings.setWallpaperAutoUpdate(value) }
    }

    fun setPaletteStyle(value: PaletteStyle) {
        viewModelScope.launch { settings.setPaletteStyle(value) }
    }

    /** 保存为我的主题, and the rename that reuses it — see `SettingsRepository.saveTheme`. */
    fun saveTheme(name: String, argb: Int) {
        viewModelScope.launch { settings.saveTheme(name, argb) }
    }

    fun deleteTheme(argb: Int) {
        viewModelScope.launch { settings.deleteSavedTheme(argb) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ThemeSettingsViewModel(settings = container.settingsRepository) }
            }
    }
}

data class ThemeSettingsUiState(val settings: UserSettings = UserSettings())

/**
 * The seed in force, whichever source is selected — the one input `PlazaTheme` needs.
 *
 * 动态取色 is the only source that has to go and look something up, and the only one where two
 * settings decide the answer:
 *
 * - with 壁纸变化时自动更新 on, the wallpaper's current lead colour wins and a pinned candidate is
 *   ignored, which is what "换壁纸后重新取色" has to mean;
 * - with it off, the pinned candidate wins and the wallpaper is only consulted when nothing is
 *   pinned yet — which happens the moment 使用系统调色板 is switched off, before the reader has
 *   picked anything. Falling through to the default there would have turned the wallpaper's colours
 *   into 石墨青 at the flick of an unrelated switch.
 *
 * Reading the palette costs one system call with no permission behind it (see `WallpaperPalette`),
 * and it is remembered, so this is not a per-frame cost.
 */
@Composable
internal fun rememberActiveSeed(settings: UserSettings): Int {
    val wallpaper =
        if (settings.colorSource == ColorSource.WALLPAPER) {
            rememberWallpaperPalette(retryKey = 0).candidates.firstOrNull()?.toArgb()
        } else {
            null
        }
    return when (settings.colorSource) {
        ColorSource.PRESET -> settings.presetSeed

        ColorSource.CUSTOM -> settings.seedColor

        ColorSource.WALLPAPER ->
            if (settings.wallpaperAutoUpdate) {
                wallpaper ?: settings.wallpaperSeed
            } else {
                settings.wallpaperSeed ?: wallpaper
            }
    } ?: SettingsRepository.DEFAULT_SEED_COLOR
}

/** `:designsys` cannot see this module's enum, so the two meet here. */
internal fun PaletteStyle.toPlaza(): PlazaPaletteStyle =
    when (this) {
        PaletteStyle.SOFT -> PlazaPaletteStyle.SOFT
        PaletteStyle.VIBRANT -> PlazaPaletteStyle.VIBRANT
        PaletteStyle.EXPRESSIVE -> PlazaPaletteStyle.EXPRESSIVE
        PaletteStyle.NEUTRAL -> PlazaPaletteStyle.NEUTRAL
        PaletteStyle.MONOCHROME -> PlazaPaletteStyle.MONOCHROME
    }
