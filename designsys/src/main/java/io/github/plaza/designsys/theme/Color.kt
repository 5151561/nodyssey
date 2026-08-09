package io.github.plaza.designsys.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette from the design doc, scheme "石墨青" (graphite teal).
 *
 * It is a fixed brand palette rather than a wallpaper-derived one: a forum client is recognised by
 * its screenshots, and dynamic color would make every install look like a different app. The
 * neutrals are deliberately blue-tinted rather than pure grey, and dark surface is #121318 rather
 * than black so that hairline dividers — the only thing separating rows in a dense list — survive.
 *
 * Every value here is a Material 3 semantic role, so components read them through
 * `MaterialTheme.colorScheme` and nothing hardcodes a hex outside this file.
 */

// --- Light ------------------------------------------------------------------

private val LightPrimary = Color(0xFF35606E)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFC0E5F5)
private val LightOnPrimaryContainer = Color(0xFF04212B)

private val LightSecondary = Color(0xFF575E71)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFDFE2EB)
private val LightOnSecondaryContainer = Color(0xFF31363F)

private val LightTertiary = Color(0xFF6D5D49)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFF2E1CC)
private val LightOnTertiaryContainer = Color(0xFF251A05)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightSurface = Color(0xFFF9F9FF)
private val LightOnSurface = Color(0xFF1A1C20)
private val LightSurfaceVariant = Color(0xFFE1E2EC)

/** 4.62:1 on [LightSurface]. Timestamps and counts must stay readable, so no light grey. */
private val LightOnSurfaceVariant = Color(0xFF44474E)

private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF3F3FA)
private val LightSurfaceContainer = Color(0xFFEDEDF4)
private val LightSurfaceContainerHigh = Color(0xFFE7E8EE)
private val LightSurfaceContainerHighest = Color(0xFFE2E2E9)

private val LightOutline = Color(0xFF74777F)
private val LightOutlineVariant = Color(0xFFC4C6D0)

// --- Dark -------------------------------------------------------------------

private val DarkPrimary = Color(0xFFA4CBDC)
private val DarkOnPrimary = Color(0xFF06333F)
private val DarkPrimaryContainer = Color(0xFF274954)
private val DarkOnPrimaryContainer = Color(0xFFC0E5F5)

private val DarkSecondary = Color(0xFFBFC6DC)
private val DarkOnSecondary = Color(0xFF29303F)
private val DarkSecondaryContainer = Color(0xFF414659)
private val DarkOnSecondaryContainer = Color(0xFFDDE1F9)

private val DarkTertiary = Color(0xFFD6C3AB)
private val DarkOnTertiary = Color(0xFF3B2E1D)
private val DarkTertiaryContainer = Color(0xFF4F4433)
private val DarkOnTertiaryContainer = Color(0xFFF0DFC9)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

/** Not black: on a pure-black surface `outlineVariant` disappears and the list loses its rows. */
private val DarkSurface = Color(0xFF121318)
private val DarkOnSurface = Color(0xFFE2E2E9)
private val DarkSurfaceVariant = Color(0xFF44474E)
private val DarkOnSurfaceVariant = Color(0xFFC4C6D0)

private val DarkSurfaceContainerLowest = Color(0xFF0D0E13)
private val DarkSurfaceContainerLow = Color(0xFF1A1B21)
private val DarkSurfaceContainer = Color(0xFF1E1F25)
private val DarkSurfaceContainerHigh = Color(0xFF292A2F)
private val DarkSurfaceContainerHighest = Color(0xFF34343A)

private val DarkOutline = Color(0xFF8E9099)
private val DarkOutlineVariant = Color(0xFF44474E)

internal val NodysseyLightColorScheme =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        inversePrimary = DarkPrimary,
        secondary = LightSecondary,
        onSecondary = LightOnSecondary,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        tertiary = LightTertiary,
        onTertiary = LightOnTertiary,
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        error = LightError,
        onError = LightOnError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        background = LightSurface,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceTint = LightPrimary,
        inverseSurface = Color(0xFF2F3036),
        inverseOnSurface = Color(0xFFF0F0F7),
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        scrim = Color(0xFF000000),
        surfaceBright = LightSurface,
        surfaceDim = Color(0xFFD9DAE0),
        surfaceContainerLowest = LightSurfaceContainerLowest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
    )

internal val NodysseyDarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        inversePrimary = LightPrimary,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = DarkTertiary,
        onTertiary = DarkOnTertiary,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        background = DarkSurface,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceTint = DarkPrimary,
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2F3036),
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF38393F),
        surfaceDim = DarkSurface,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
    )

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
data class NodysseyExtraColors(
    val warningContainer: Color,
    val onWarningContainer: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val success: Color,
    val warning: Color,
)

internal val LightExtraColors =
    NodysseyExtraColors(
        warningContainer = Color(0xFFF7E3A6),
        onWarningContainer = Color(0xFF4E3D00),
        successContainer = Color(0xFFBFE9C8),
        onSuccessContainer = Color(0xFF0A2E15),
        success = Color(0xFF1B6B3A),
        warning = Color(0xFF7A5A00),
    )

internal val DarkExtraColors =
    NodysseyExtraColors(
        warningContainer = Color(0xFF4E4426),
        onWarningContainer = Color(0xFFF7E3A6),
        successContainer = Color(0xFF244A2F),
        onSuccessContainer = Color(0xFFBFE9C8),
        success = Color(0xFF7FD79B),
        warning = Color(0xFFE5C07B),
    )

val LocalNodysseyExtraColors = staticCompositionLocalOf { LightExtraColors }
