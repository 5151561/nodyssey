package io.github.plaza.designsys.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot

/**
 * Where the app's colours come from.
 *
 * The three are mutually exclusive by nature — a scheme has exactly one origin — so they are one
 * choice rather than a pile of switches. [WALLPAPER] falls back to [BRAND] below API 31, which is
 * decided in [PlazaTheme] rather than here: the setting stores what the reader asked for, and a
 * phone that cannot honour it should not silently rewrite their answer.
 */
enum class PlazaColorSource { BRAND, WALLPAPER, SEED }

/**
 * The seed a fresh install starts [PlazaColorSource.SEED] on: 石墨青's own `primary`.
 *
 * Picking the brand tone means switching from 品牌配色 to 自选颜色 without touching anything else is a
 * near-no-op rather than a jump to some unrelated hue — the generated scheme is not identical to the
 * hand-tuned one, but it is recognisably the same app while the reader goes looking for their colour.
 */
val PlazaDefaultSeed = Color(0xFF35606E)

/**
 * A seed colour in the coordinates the generator actually reads.
 *
 * The picker moves 色相 and 鲜艳度 because those are the two the scheme is derived from — HCT hue and
 * chroma. [tone] is carried along so that a colour typed in as hex and then nudged one notch comes
 * back out as the same colour, rather than snapping to whatever lightness the sliders happened to
 * default to.
 */
@Immutable
data class PlazaSeedHct(
    val hue: Float,
    val chroma: Float,
    val tone: Float,
) {
    fun toColor(): Color = Color(Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).toInt())

    companion object {
        /** What the sliders span. Chroma above ~120 is unreachable in sRGB at any hue. */
        val HUE_RANGE = 0f..360f
        val CHROMA_RANGE = 0f..120f
    }
}

/** Reads a colour back into the three coordinates the picker edits. */
fun Color.toPlazaSeedHct(): PlazaSeedHct =
    Hct.fromInt(toArgb()).let { PlazaSeedHct(it.hue.toFloat(), it.chroma.toFloat(), it.tone.toFloat()) }

/**
 * Expands one seed colour into a full Material 3 scheme.
 *
 * This is the same algorithm the system runs on the wallpaper — HCT, five tonal palettes, then a
 * contrast-aware tone per role — so 自选颜色 and 壁纸取色 produce schemes of the same character
 * instead of two different-looking themes sharing a settings screen.
 *
 * `TONAL_SPOT` and `SPEC_2021` are pinned rather than left to the library's defaults. TonalSpot is
 * the variant Android itself uses, which is what makes the two sources match; the 2021 spec is what
 * produced every wallpaper palette on every phone this app runs on, so pinning it keeps the match
 * true. A newer default arriving in a library bump must not silently restyle everyone's app.
 *
 * Only the seed's hue and chroma reach the palettes — its tone does not — so #35606E and a lighter
 * tint of the same teal land on the same scheme, give or take the step that rounding a colour to
 * eight bits per channel costs. That is the algorithm's design, not a bug here: the seed names a
 * colour family, and the scheme decides the lightness each role needs. The exception is a seed close
 * to black or white, where the chroma it claims does not fit in sRGB and gets clipped on the way in.
 */
fun plazaSeedColorScheme(seed: Color, darkTheme: Boolean): ColorScheme {
    val scheme =
        SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(seed.toArgb()),
            isDark = darkTheme,
            contrastLevel = 0.0,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
            platform = DynamicScheme.Platform.PHONE,
        )
    return if (darkTheme) {
        darkColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            inversePrimary = Color(scheme.inversePrimary),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            surfaceTint = Color(scheme.surfaceTint),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            scrim = Color(scheme.scrim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            primaryFixed = Color(scheme.primaryFixed),
            primaryFixedDim = Color(scheme.primaryFixedDim),
            onPrimaryFixed = Color(scheme.onPrimaryFixed),
            onPrimaryFixedVariant = Color(scheme.onPrimaryFixedVariant),
            secondaryFixed = Color(scheme.secondaryFixed),
            secondaryFixedDim = Color(scheme.secondaryFixedDim),
            onSecondaryFixed = Color(scheme.onSecondaryFixed),
            onSecondaryFixedVariant = Color(scheme.onSecondaryFixedVariant),
            tertiaryFixed = Color(scheme.tertiaryFixed),
            tertiaryFixedDim = Color(scheme.tertiaryFixedDim),
            onTertiaryFixed = Color(scheme.onTertiaryFixed),
            onTertiaryFixedVariant = Color(scheme.onTertiaryFixedVariant),
        )
    } else {
        lightColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            inversePrimary = Color(scheme.inversePrimary),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            surfaceTint = Color(scheme.surfaceTint),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            scrim = Color(scheme.scrim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            primaryFixed = Color(scheme.primaryFixed),
            primaryFixedDim = Color(scheme.primaryFixedDim),
            onPrimaryFixed = Color(scheme.onPrimaryFixed),
            onPrimaryFixedVariant = Color(scheme.onPrimaryFixedVariant),
            secondaryFixed = Color(scheme.secondaryFixed),
            secondaryFixedDim = Color(scheme.secondaryFixedDim),
            onSecondaryFixed = Color(scheme.onSecondaryFixed),
            onSecondaryFixedVariant = Color(scheme.onSecondaryFixedVariant),
            tertiaryFixed = Color(scheme.tertiaryFixed),
            tertiaryFixedDim = Color(scheme.tertiaryFixedDim),
            onTertiaryFixed = Color(scheme.onTertiaryFixed),
            onTertiaryFixedVariant = Color(scheme.onTertiaryFixedVariant),
        )
    }
}
