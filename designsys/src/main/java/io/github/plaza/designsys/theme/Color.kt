package io.github.plaza.designsys.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * The app's own palette used to live here as a pair of hand-tuned `ColorScheme`s. It does not any
 * more: 主题 offers six presets, a wallpaper and a hand-picked seed, all of them expanded by the same
 * generator (see `plazaSeedColorScheme`), and a seventh scheme that answered to neither 色彩风格 nor
 * the preview card would have been the one entry the screen could not describe. 石墨青 survives as
 * `PlazaDefaultSeed` — the same #35606E, now as a seed.
 */

/**
 * The tonal pairs Material 3 has no role for.
 *
 * Board tags are grouped into four colour families (see `BoardTag`), and the fourth — the boards
 * that carry a warning ("曝光", "内版") — needs a warm amber that is neither `error` nor any of the
 * three brand tones. Keeping it in a [staticCompositionLocalOf] rather than a top-level `val` is
 * what lets it flip with the theme like every other token.
 *
 * `success` joins it for the benchmark reports: those mark a check as passed or failed, and Material
 * gives a role to only half of that pair. Green rather than a brand tone because the reports are
 * read as a verdict — `error` opposite `primary` would read as "bad" opposite "branded".
 *
 * [success] and [warning] are the ink of that pair rather than its fill, and they are separate
 * colours rather than the `on…Container` ones: a container's ink is chosen to sit on that container,
 * and the report cards write a green 低风险 straight onto the card surface, where the near-black
 * `onSuccessContainer` would read as ordinary text. `error` already plays this role for the third.
 */
@Immutable
data class PlazaExtraColors(
    val warningContainer: Color,
    val onWarningContainer: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val success: Color,
    val warning: Color,
)

internal val LightExtraColors =
    PlazaExtraColors(
        warningContainer = Color(0xFFF7E3A6),
        onWarningContainer = Color(0xFF4E3D00),
        successContainer = Color(0xFFBFE9C8),
        onSuccessContainer = Color(0xFF0A2E15),
        success = Color(0xFF1B6B3A),
        warning = Color(0xFF7A5A00),
    )

internal val DarkExtraColors =
    PlazaExtraColors(
        warningContainer = Color(0xFF4E4426),
        onWarningContainer = Color(0xFFF7E3A6),
        successContainer = Color(0xFF244A2F),
        onSuccessContainer = Color(0xFFBFE9C8),
        success = Color(0xFF7FD79B),
        warning = Color(0xFFE5C07B),
    )

val LocalPlazaExtraColors = staticCompositionLocalOf { LightExtraColors }
