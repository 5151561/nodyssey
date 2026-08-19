package io.github.plaza.designsys.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

/**
 * 角色预设 — the five schemes 主题 offers that are not grown from a seed.
 *
 * Every other colour in the app is one seed run through [plazaSeedColorScheme]. These five are not:
 * each is a hand-written set of Material roles, because a character is a *combination* — 初音未来 is
 * teal against grey with pink on the hair ties, and a generator handed #39C5BB returns a competent
 * Tiffany-teal app with no pink anywhere in it. The third colour is the whole recognition, and it is
 * the one thing a single seed cannot carry.
 *
 * Two consequences follow, and both are deliberate:
 *
 * - 色彩风格 does nothing here. It steers the generator, and there is no generator in this path —
 *   the settings screen greys the chips out rather than leaving five controls that no longer move.
 * - The character's own colour is usually *not* `primary`. #39C5BB on white fails contrast under
 *   white text, so `primary` is the darkened #008078 and the recognisable teal lives in
 *   `primaryContainer` and in dark mode's `primary`, where it has the ground to be itself on.
 *
 * The values are literals from the j2 board (`tokens/presets.css` there), which is also where the
 * contrast was checked — every `on…` pair at 4.5:1 and body text at 7:1.
 */
enum class PlazaCharacterPalette(
    private val light: CharacterTokens,
    private val dark: CharacterTokens,
) {
    /** 初音未来 · 青 × 灰 × 粉 */
    MIKU(
        light =
        CharacterTokens(
            primary = 0xFF008078, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFB7F0EC, onPrimaryContainer = 0xFF003736,
            secondaryContainer = 0xFFD7E5EA, onSecondaryContainer = 0xFF33454F,
            tertiaryContainer = 0xFFFFD9E4, onTertiaryContainer = 0xFF501530,
            onSurfaceVariant = 0xFF435654, outline = 0xFF6F8583, outlineVariant = 0xFFC0D2D0,
            surface = 0xFFF8FCFC, onSurface = 0xFF121C1B,
            surfaceContainerLow = 0xFFF5FAFA, surfaceContainer = 0xFFEFF5F5,
            surfaceContainerHigh = 0xFFE8F0EF, surfaceContainerHighest = 0xFFDCE8E7,
        ),
        dark =
        CharacterTokens(
            primary = 0xFF53D7CE, onPrimary = 0xFF003735,
            primaryContainer = 0xFF00504E, onPrimaryContainer = 0xFFB7F0EC,
            secondaryContainer = 0xFF354751, onSecondaryContainer = 0xFFD7E5EA,
            tertiaryContainer = 0xFF5E2340, onTertiaryContainer = 0xFFFFD9E4,
            onSurfaceVariant = 0xFFA9C4C1, outline = 0xFF7E9997, outlineVariant = 0xFF33454F,
            surface = 0xFF091414, onSurface = 0xFFD9E5E4,
            surfaceContainerLow = 0xFF0E1B1B, surfaceContainer = 0xFF122220,
            surfaceContainerHigh = 0xFF182B2A, surfaceContainerHighest = 0xFF19302F,
        ),
    ),

    /** 镜音双子 · 黄 × 橙 × 黑灰 */
    TWINS(
        light =
        CharacterTokens(
            primary = 0xFF8F6F00, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFFFE68A, onPrimaryContainer = 0xFF453500,
            secondaryContainer = 0xFFFFDCC8, onSecondaryContainer = 0xFF5C2C00,
            tertiaryContainer = 0xFFDEE3EA, onTertiaryContainer = 0xFF3F4854,
            onSurfaceVariant = 0xFF4E463A, outline = 0xFF887D6C, outlineVariant = 0xFFD2C9B8,
            surface = 0xFFFFFBF4, onSurface = 0xFF1E1B12,
            surfaceContainerLow = 0xFFFFF9EE, surfaceContainer = 0xFFF7F1E4,
            surfaceContainerHigh = 0xFFF1EBDE, surfaceContainerHighest = 0xFFEEE7DA,
        ),
        dark =
        CharacterTokens(
            primary = 0xFFFFD84A, onPrimary = 0xFF3B2D00,
            primaryContainer = 0xFF5C4900, onPrimaryContainer = 0xFFFFE68A,
            secondaryContainer = 0xFF6E3A12, onSecondaryContainer = 0xFFFFDCC8,
            tertiaryContainer = 0xFF414A56, onTertiaryContainer = 0xFFDEE3EA,
            onSurfaceVariant = 0xFFCFC6B4, outline = 0xFF99917E, outlineVariant = 0xFF4E463A,
            surface = 0xFF17140C, onSurface = 0xFFEBE3D2,
            surfaceContainerLow = 0xFF201C11, surfaceContainer = 0xFF252012,
            surfaceContainerHigh = 0xFF2E2818, surfaceContainerHighest = 0xFF383115,
        ),
    ),

    /** 洛天依 · 蓝 × 白 × 粉 */
    TIANYI(
        light =
        CharacterTokens(
            primary = 0xFF1478B2, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFC7ECFF, onPrimaryContainer = 0xFF00344C,
            secondaryContainer = 0xFFD1F1FC, onSecondaryContainer = 0xFF1A4A57,
            tertiaryContainer = 0xFFFFD9E5, onTertiaryContainer = 0xFF55112F,
            onSurfaceVariant = 0xFF42545E, outline = 0xFF71858E, outlineVariant = 0xFFC0D7E2,
            surface = 0xFFF8FCFF, onSurface = 0xFF14191D,
            surfaceContainerLow = 0xFFF3FAFD, surfaceContainer = 0xFFECF5FA,
            surfaceContainerHigh = 0xFFE5F0F7, surfaceContainerHighest = 0xFFDDEDF4,
        ),
        dark =
        CharacterTokens(
            primary = 0xFF72CEFA, onPrimary = 0xFF00344C,
            primaryContainer = 0xFF00527A, onPrimaryContainer = 0xFFC7ECFF,
            secondaryContainer = 0xFF1D4B5B, onSecondaryContainer = 0xFFD1F1FC,
            tertiaryContainer = 0xFF63213B, onTertiaryContainer = 0xFFFFD9E5,
            onSurfaceVariant = 0xFFB4CBD8, outline = 0xFF7E97A4, outlineVariant = 0xFF2E4A59,
            surface = 0xFF08151C, onSurface = 0xFFD8E4EB,
            surfaceContainerLow = 0xFF0C202A, surfaceContainer = 0xFF102632,
            surfaceContainerHigh = 0xFF152D3A, surfaceContainerHighest = 0xFF173341,
        ),
    ),

    /** 博丽灵梦 · 红 × 白 × 金 */
    REIMU(
        light =
        CharacterTokens(
            primary = 0xFFBC3038, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFFFDADB, onPrimaryContainer = 0xFF410007,
            secondaryContainer = 0xFFFFD9DE, onSecondaryContainer = 0xFF5E1F2A,
            tertiaryContainer = 0xFFFFE08A, onTertiaryContainer = 0xFF423000,
            onSurfaceVariant = 0xFF534241, outline = 0xFF8C706F, outlineVariant = 0xFFD9C1BF,
            surface = 0xFFFFF9F8, onSurface = 0xFF201717,
            surfaceContainerLow = 0xFFFFF7F6, surfaceContainer = 0xFFF9EFEE,
            surfaceContainerHigh = 0xFFF5E8E7, surfaceContainerHighest = 0xFFF2DEDC,
        ),
        dark =
        CharacterTokens(
            primary = 0xFFFFB3B6, onPrimary = 0xFF67000E,
            primaryContainer = 0xFF8F1522, onPrimaryContainer = 0xFFFFDADB,
            secondaryContainer = 0xFF653039, onSecondaryContainer = 0xFFFFD9DE,
            tertiaryContainer = 0xFF5A4300, onTertiaryContainer = 0xFFFFE08A,
            onSurfaceVariant = 0xFFD9BFBD, outline = 0xFFA18684, outlineVariant = 0xFF574140,
            surface = 0xFF1B1010, onSurface = 0xFFEFDFDD,
            surfaceContainerLow = 0xFF251516, surfaceContainer = 0xFF2A191A,
            surfaceContainerHigh = 0xFF33201F, surfaceContainerHighest = 0xFF3D2726,
        ),
    ),

    /** 雾雨魔理沙 · 黑 × 金 × 紫 */
    MARISA(
        light =
        CharacterTokens(
            primary = 0xFF8A6C00, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFFFE582, onPrimaryContainer = 0xFF3A3000,
            secondaryContainer = 0xFFE8DFE7, onSecondaryContainer = 0xFF3D383F,
            tertiaryContainer = 0xFFF0D8FF, onTertiaryContainer = 0xFF3F2455,
            onSurfaceVariant = 0xFF4C464D, outline = 0xFF80777F, outlineVariant = 0xFFCCC4CB,
            surface = 0xFFFCF9F5, onSurface = 0xFF1C1A17,
            surfaceContainerLow = 0xFFF8F5F1, surfaceContainer = 0xFFF2EFEA,
            surfaceContainerHigh = 0xFFEDE9E4, surfaceContainerHighest = 0xFFE9E2E7,
        ),
        dark =
        CharacterTokens(
            primary = 0xFFF4D657, onPrimary = 0xFF3A3000,
            primaryContainer = 0xFF554800, onPrimaryContainer = 0xFFFFE582,
            secondaryContainer = 0xFF423D44, onSecondaryContainer = 0xFFE8DFE7,
            tertiaryContainer = 0xFF573E6E, onTertiaryContainer = 0xFFF0D8FF,
            onSurfaceVariant = 0xFFCDC5CE, outline = 0xFF918A93, outlineVariant = 0xFF47424B,
            surface = 0xFF111014, onSurface = 0xFFE6E1E5,
            surfaceContainerLow = 0xFF19171C, surfaceContainer = 0xFF1E1C21,
            surfaceContainerHigh = 0xFF252229, surfaceContainerHighest = 0xFF29252D,
        ),
    ),
    ;

    // Built once per palette rather than per composition: each one costs a seed expansion plus four
    // HCT solves, and `PlazaTheme` would otherwise redo that every time the app re-themes.
    private val lightScheme by lazy { light.toColorScheme(darkTheme = false, seed = light.primary) }
    private val darkScheme by lazy { dark.toColorScheme(darkTheme = true, seed = light.primary) }

    fun colorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) darkScheme else lightScheme
}

/**
 * The roles the board writes by hand — seventeen of the forty-odd Material carries.
 *
 * `Long` rather than `Color` so the tables above read as the hex the board printed. The roles that
 * are *not* here are the ones a character has no opinion about: `error` is red because a failure is
 * red, and the `…Fixed` pairs are only reached by components this app does not draw. Those come from
 * the seed expansion in [toColorScheme], which keeps them in the same colour family instead of
 * leaving Material's baseline purple behind.
 */
@Immutable
internal data class CharacterTokens(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,
    val onSurfaceVariant: Long,
    val outline: Long,
    val outlineVariant: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceContainerLow: Long,
    val surfaceContainer: Long,
    val surfaceContainerHigh: Long,
    val surfaceContainerHighest: Long,
)

/**
 * Hand tokens over a generated ground.
 *
 * The ground is the same generator every other scheme in the app runs on, seeded with the palette's
 * *light* `primary` in both modes so light and dark are two views of one colour family rather than
 * two unrelated schemes. Everything the board specified is then written over it.
 *
 * `secondary` and `tertiary` are neither hand-written nor left to the seed: the board gives their
 * containers, and a standalone role that drifted to the seed's hue would put a teal icon next to an
 * orange chip. They are derived from the container instead — same hue and chroma, moved to the tone
 * Material puts a standalone role at, which is tone 40 in light and 80 in dark.
 */
private fun CharacterTokens.toColorScheme(darkTheme: Boolean, seed: Long): ColorScheme {
    val roleTone = if (darkTheme) DARK_ROLE_TONE else LIGHT_ROLE_TONE
    val onRoleTone = if (darkTheme) DARK_ON_ROLE_TONE else LIGHT_ON_ROLE_TONE
    return plazaSeedColorScheme(Color(seed), darkTheme, PlazaPaletteStyle.SOFT).copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondaryContainer).atTone(roleTone),
        onSecondary = Color(secondaryContainer).atTone(onRoleTone),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiaryContainer).atTone(roleTone),
        onTertiary = Color(tertiaryContainer).atTone(onRoleTone),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(surface),
        onBackground = Color(onSurface),
        surface = Color(surface),
        onSurface = Color(onSurface),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
    )
}

/** The same colour, moved to another lightness — hue and chroma untouched. */
private fun Color.atTone(tone: Double): Color =
    Hct.fromInt(toArgb()).let { Color(Hct.from(it.hue, it.chroma, tone).toInt()) }

private const val LIGHT_ROLE_TONE = 40.0
private const val DARK_ROLE_TONE = 80.0
private const val LIGHT_ON_ROLE_TONE = 100.0
private const val DARK_ON_ROLE_TONE = 20.0
