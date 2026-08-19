package io.github.nodyssey.ui.settings.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.nodyssey.R
import java.util.Locale

/**
 * The six 预设, as j1 lays them out: three columns, 石墨青 first.
 *
 * Six rather than the nine an earlier build offered. Nine filled three rows and turned a choice into
 * a chart; six fits two, and the reader who wants a seventh colour has 自定义 one section below —
 * which is the section the extra three were standing in for.
 *
 * 石墨青 leads because it is the app's own colour, and picking it is the closest thing to "put it
 * back". It is a seed like the other five: the hand-tuned palette it used to name is gone, so
 * 色彩风格 and the preview card describe every entry in the grid rather than five of six.
 */
internal data class ThemePreset(
    @param:StringRes val label: Int,
    val seed: Color,
    /** The dot's other half. See [ThemeSwatch] — and the note below on why it is a literal. */
    val companion: Color,
)

/*
 * The companions are literals rather than each seed's generated `tertiary`.
 *
 * They are sample colours, not theme tokens: a dot has to look the same in light and dark or the
 * grid stops being a stable thing to point at, and the generated tertiary moves with both the mode
 * and 色彩风格. j1 marks them the same way — "字面色值例外 … 亮暗两态相同" — alongside the avatar
 * ground rule.
 */
internal val ThemePresets =
    listOf(
        ThemePreset(R.string.settings_preset_teal, Color(0xFF35606E), Color(0xFF7E5700)),
        ThemePreset(R.string.settings_preset_warm_graphite, Color(0xFF7A6A54), Color(0xFF4C6B4A)),
        ThemePreset(R.string.settings_preset_indigo, Color(0xFF4C5FA8), Color(0xFF7A4E93)),
        ThemePreset(R.string.settings_preset_moss, Color(0xFF3F6B4E), Color(0xFF6B6428)),
        ThemePreset(R.string.settings_preset_rose, Color(0xFF8B4F72), Color(0xFF8A5340)),
        ThemePreset(R.string.settings_preset_sunset, Color(0xFFA05A32), Color(0xFF6B6428)),
    )

/** `#RRGGBB`, upper case — the form the hex field reads back and the form the labels print. */
internal fun Color.toHexString(): String =
    String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

/** `#RRGGBB` or bare `RRGGBB`; anything else is still being typed. */
internal fun parseHexColor(text: String): Color? {
    val digits = text.removePrefix("#")
    if (digits.length != 6 || digits.any { it.digitToIntOrNull(16) == null }) return null
    return Color(0xFF000000.toInt() or digits.toInt(16))
}
