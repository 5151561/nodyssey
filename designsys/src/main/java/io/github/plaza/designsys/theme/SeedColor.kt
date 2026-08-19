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
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/**
 * How far a scheme is allowed to travel from its seed.
 *
 * These are the five Material variants the algorithm ships, named for what they do to the reader's
 * colour rather than for the class behind them: [SOFT] is the one Android itself runs on a
 * wallpaper, and the default here for the same reason — it is the only one that produces a scheme
 * nobody has to think about. The other four exist because a seed is a request, and a reader who
 * picked a red wants some say in whether the app comes back red or merely warm.
 *
 * The four that are not [SOFT] rotate or crush the hue on purpose. [MONOCHROME] discards it
 * entirely; that is the point of it, and not a seed that failed to reach the scheme.
 */
enum class PlazaPaletteStyle { SOFT, VIBRANT, EXPRESSIVE, NEUTRAL, MONOCHROME }

/**
 * The seed a fresh install starts on: 石墨青, the colour the app has always been.
 *
 * It is a seed rather than a hand-tuned palette because every one of the six presets is, and a first
 * preset that answered to neither 色彩风格 nor the preview card would have been the one entry in the
 * grid the rest of the screen could not describe.
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
 * contrast-aware tone per role — so 自定义 and 动态取色 produce schemes of the same character instead
 * of two different-looking themes sharing a settings screen.
 *
 * `SPEC_2021` is pinned rather than left to the library's default: it is what produced every
 * wallpaper palette on every phone this app runs on, so pinning it keeps 动态取色 matching the system
 * it borrows from. A newer default arriving in a library bump must not silently restyle everyone's
 * app.
 *
 * Only the seed's hue and chroma reach the palettes — its tone does not — so #35606E and a lighter
 * tint of the same teal land on the same scheme, give or take the step that rounding a colour to
 * eight bits per channel costs. That is the algorithm's design, not a bug here: the seed names a
 * colour family, and the scheme decides the lightness each role needs. The exception is a seed close
 * to black or white, where the chroma it claims does not fit in sRGB and gets clipped on the way in.
 */
fun plazaSeedColorScheme(
    seed: Color,
    darkTheme: Boolean,
    style: PlazaPaletteStyle = PlazaPaletteStyle.SOFT,
): ColorScheme {
    val source = Hct.fromInt(seed.toArgb())
    val spec = ColorSpec.SpecVersion.SPEC_2021
    val platform = DynamicScheme.Platform.PHONE
    val scheme =
        when (style) {
            PlazaPaletteStyle.SOFT -> SchemeTonalSpot(source, darkTheme, 0.0, spec, platform)
            PlazaPaletteStyle.VIBRANT -> SchemeVibrant(source, darkTheme, 0.0, spec, platform)
            PlazaPaletteStyle.EXPRESSIVE -> SchemeExpressive(source, darkTheme, 0.0, spec, platform)
            PlazaPaletteStyle.NEUTRAL -> SchemeNeutral(source, darkTheme, 0.0, spec, platform)
            PlazaPaletteStyle.MONOCHROME -> SchemeMonochrome(source, darkTheme, 0.0, spec, platform)
        }
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
