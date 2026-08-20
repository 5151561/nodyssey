package io.github.nodyssey.ui.settings.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.preset_avatar_marisa
import io.github.nodyssey.ui.resources.preset_avatar_miku
import io.github.nodyssey.ui.resources.preset_avatar_reimu
import io.github.nodyssey.ui.resources.preset_avatar_tianyi
import io.github.nodyssey.ui.resources.preset_avatar_twins
import io.github.nodyssey.ui.resources.settings_preset_graphite
import io.github.nodyssey.ui.resources.settings_preset_marisa
import io.github.nodyssey.ui.resources.settings_preset_miku
import io.github.nodyssey.ui.resources.settings_preset_reimu
import io.github.nodyssey.ui.resources.settings_preset_tianyi
import io.github.nodyssey.ui.resources.settings_preset_twins
import io.github.plaza.designsys.theme.PlazaCharacterPalette
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * The six 预设, as j2 lays them out: three columns, 石墨青 first, five characters after it.
 *
 * 石墨青 leads and stays the factory default because it is the app's own colour — picking it is the
 * closest thing to "put it back". It is also the only one of the six that is still a seed: the other
 * five are whole hand-written schemes (see [PlazaCharacterPalette]), which is the point of them. A
 * character is a combination of colours, and the generator only ever gets handed one.
 *
 * The five that j1 offered — 暖石墨, 靛蓝, 苔绿, 玫紫, 落日橙 — are gone rather than pushed to a
 * second plate. They were seeds with names, and the section one below this one is where a reader
 * makes exactly that: 自定义 takes any colour at all and 我的主题 keeps it.
 */
internal data class ThemePreset(
    /** What the store holds. Stable across releases — the label is not. */
    val id: String,
    /**
     * The one line under the dot: 石墨青's name, or one of the five schemes' three-colour summary —
     * 青×灰×粉 and the rest.
     */
    val label: StringResource,
    /** The hand-written scheme, or null for 石墨青 — the one preset still expanded from a seed. */
    val palette: PlazaCharacterPalette?,
    /** The flat portrait, or null for 石墨青, which is a two-tone dot rather than a face. */
    val avatar: DrawableResource?,
)

internal val ThemePresets =
    listOf(
        ThemePreset(
            id = SettingsRepository.DEFAULT_PRESET_ID,
            label = Res.string.settings_preset_graphite,
            palette = null,
            avatar = null,
        ),
        ThemePreset(
            id = "miku",
            label = Res.string.settings_preset_miku,
            palette = PlazaCharacterPalette.MIKU,
            avatar = Res.drawable.preset_avatar_miku,
        ),
        ThemePreset(
            id = "twins",
            label = Res.string.settings_preset_twins,
            palette = PlazaCharacterPalette.TWINS,
            avatar = Res.drawable.preset_avatar_twins,
        ),
        ThemePreset(
            id = "tianyi",
            label = Res.string.settings_preset_tianyi,
            palette = PlazaCharacterPalette.TIANYI,
            avatar = Res.drawable.preset_avatar_tianyi,
        ),
        ThemePreset(
            id = "reimu",
            label = Res.string.settings_preset_reimu,
            palette = PlazaCharacterPalette.REIMU,
            avatar = Res.drawable.preset_avatar_reimu,
        ),
        ThemePreset(
            id = "marisa",
            label = Res.string.settings_preset_marisa,
            palette = PlazaCharacterPalette.MARISA,
            avatar = Res.drawable.preset_avatar_marisa,
        ),
    )

/** The preset a stored id names, or 石墨青 when the store holds one this build no longer ships. */
internal fun presetById(id: String): ThemePreset =
    ThemePresets.firstOrNull { it.id == id } ?: ThemePresets.first()

/**
 * 石墨青's other half — the dot's warm side, split on the 135° diagonal.
 *
 * A literal rather than the seed's generated `tertiary`: it is a sample colour, not a theme token,
 * and a dot that moved with 色彩风格 and the mode would stop being a stable thing to point at. j2
 * marks it the same way — "字面色值例外".
 */
internal val GraphiteCompanion = Color(0xFF7E5700)

/** `#RRGGBB`, upper case — the form the hex field reads back and the form the labels print. */
internal fun Color.toHexString(): String =
    "#" + (toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** `#RRGGBB` or bare `RRGGBB`; anything else is still being typed. */
internal fun parseHexColor(text: String): Color? {
    val digits = text.removePrefix("#")
    if (digits.length != 6 || digits.any { it.digitToIntOrNull(16) == null }) return null
    return Color(0xFF000000.toInt() or digits.toInt(16))
}
