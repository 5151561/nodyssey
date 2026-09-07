package io.github.plaza.designsys.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What 墨水屏模式's scheme promises, written as assertions rather than as a comment.
 *
 * A screenshot would say "this changed"; these say "this is no longer a scheme for electronic
 * paper", which is the claim that matters and the one a well-meaning edit is most likely to break —
 * every one of these can be violated by a change that looks like an improvement on a colour display.
 */
class EInkColorSchemeTest {
    /**
     * Four greys and black, and not one more.
     *
     * The number is the design. A panel renders sixteen levels and dithers everything between, so
     * each extra value is another flat fill that can come out as a screen door — and the way extra
     * values get in is one role at a time, each defensible on its own.
     */
    @Test
    fun `墨水屏 uses five colour values`() {
        val distinct = EInkColorScheme.everyRole().map { it.second.toArgb() }.toSet()
        assertTrue(
            distinct.size <= 5,
            "expected at most 5 distinct values, got ${distinct.size}: " +
                distinct.sorted().joinToString { it.toUInt().toString(16).uppercase() },
        )
    }

    /** Every one of them a true grey: a panel has no channels to tell #FFFEFE from #FFFFFF. */
    @Test
    fun `every role is a true grey`() {
        for ((role, color) in EInkColorScheme.everyRole()) {
            assertEquals(color.red, color.green, "$role is not grey: ${hex(color)}")
            assertEquals(color.green, color.blue, "$role is not grey: ${hex(color)}")
        }
    }

    /**
     * The containers that float above the content keep a fill of their own.
     *
     * This is the one that will look wrong to somebody later: flattening the whole container ladder
     * onto `surface` is what "pure black and white" sounds like it means, and it deletes tonal
     * elevation, which is wanted. What it also deletes is the only edge `ModalBottomSheet`,
     * `NavigationBar`, `DropdownMenu`, `AlertDialog`, `SearchBar` and `Card` have — none of them
     * takes a border, and `DropdownMenu` keeps its container out of reach — so a sheet would open
     * over a page and be invisible. The one shared grey is what pays for all six.
     */
    @Test
    fun `containers that float keep an edge`() {
        val floating =
            listOf(
                "surfaceContainerLow" to EInkColorScheme.surfaceContainerLow,
                "surfaceContainer" to EInkColorScheme.surfaceContainer,
                "surfaceContainerHigh" to EInkColorScheme.surfaceContainerHigh,
                "surfaceContainerHighest" to EInkColorScheme.surfaceContainerHighest,
            )
        for ((role, color) in floating) {
            assertNotEquals(
                EInkColorScheme.surface,
                color,
                "$role is the same as surface — every sheet, menu and dialog loses its boundary",
            )
        }
    }

    /**
     * Every ink/ground pair at 7:1, not the 4.5:1 the generated schemes are held to.
     *
     * Reaching further than WCAG AA is the entire reason this scheme exists: a reflective panel
     * under a bedside lamp has a fraction of the contrast the same pair has on a phone.
     */
    @Test
    fun `every text and ground pair is at maximum contrast`() {
        val pairs =
            listOf(
                "onSurface" to (EInkColorScheme.onSurface to EInkColorScheme.surface),
                "onBackground" to (EInkColorScheme.onBackground to EInkColorScheme.background),
                "onSurfaceVariant on surface" to
                    (EInkColorScheme.onSurfaceVariant to EInkColorScheme.surface),
                "onSurfaceVariant on surfaceVariant" to
                    (EInkColorScheme.onSurfaceVariant to EInkColorScheme.surfaceVariant),
                "onSurface on the container ladder" to
                    (EInkColorScheme.onSurface to EInkColorScheme.surfaceContainerHighest),
                "onPrimary" to (EInkColorScheme.onPrimary to EInkColorScheme.primary),
                "onPrimaryContainer" to
                    (EInkColorScheme.onPrimaryContainer to EInkColorScheme.primaryContainer),
                "onSecondary" to (EInkColorScheme.onSecondary to EInkColorScheme.secondary),
                "onSecondaryContainer" to
                    (EInkColorScheme.onSecondaryContainer to EInkColorScheme.secondaryContainer),
                "onTertiary" to (EInkColorScheme.onTertiary to EInkColorScheme.tertiary),
                "onTertiaryContainer" to
                    (EInkColorScheme.onTertiaryContainer to EInkColorScheme.tertiaryContainer),
                "onError" to (EInkColorScheme.onError to EInkColorScheme.error),
                "onErrorContainer" to
                    (EInkColorScheme.onErrorContainer to EInkColorScheme.errorContainer),
                "inverseOnSurface" to
                    (EInkColorScheme.inverseOnSurface to EInkColorScheme.inverseSurface),
                "onPrimaryFixedVariant" to
                    (EInkColorScheme.onPrimaryFixedVariant to EInkColorScheme.primaryFixed),
                "onWarningContainer" to
                    (EInkExtraColors.onWarningContainer to EInkExtraColors.warningContainer),
                "onSuccessContainer" to
                    (EInkExtraColors.onSuccessContainer to EInkExtraColors.successContainer),
            )
        for ((role, pair) in pairs) {
            val ratio = contrastRatio(pair.first, pair.second)
            assertTrue(ratio >= 7.0, "$role is only $ratio:1")
        }
    }

    /**
     * The accents really are one colour, and the test says so rather than leaving it to be
     * discovered.
     *
     * 测评报告 draws 低风险 in `success` and 高风险 in `error`, and here they are the same black. It
     * is not a bug to be fixed by finding a distinguishable grey — a grey far enough from black to
     * be told apart is a grey too light to read as a warning. What separates them is the wording.
     */
    @Test
    fun `the accent roles all collapse onto ink`() {
        assertEquals(EInkColorScheme.primary, EInkColorScheme.secondary)
        assertEquals(EInkColorScheme.primary, EInkColorScheme.tertiary)
        assertEquals(EInkColorScheme.primary, EInkColorScheme.error)
        assertEquals(EInkColorScheme.primary, EInkExtraColors.success)
        assertEquals(EInkColorScheme.primary, EInkExtraColors.warning)
    }

    private companion object {
        /** All forty-eight, named, so that a role left at Material's baseline purple is caught. */
        fun ColorScheme.everyRole(): List<Pair<String, Color>> =
            listOf(
                "primary" to primary,
                "onPrimary" to onPrimary,
                "primaryContainer" to primaryContainer,
                "onPrimaryContainer" to onPrimaryContainer,
                "inversePrimary" to inversePrimary,
                "secondary" to secondary,
                "onSecondary" to onSecondary,
                "secondaryContainer" to secondaryContainer,
                "onSecondaryContainer" to onSecondaryContainer,
                "tertiary" to tertiary,
                "onTertiary" to onTertiary,
                "tertiaryContainer" to tertiaryContainer,
                "onTertiaryContainer" to onTertiaryContainer,
                "background" to background,
                "onBackground" to onBackground,
                "surface" to surface,
                "onSurface" to onSurface,
                "surfaceVariant" to surfaceVariant,
                "onSurfaceVariant" to onSurfaceVariant,
                "surfaceTint" to surfaceTint,
                "inverseSurface" to inverseSurface,
                "inverseOnSurface" to inverseOnSurface,
                "error" to error,
                "onError" to onError,
                "errorContainer" to errorContainer,
                "onErrorContainer" to onErrorContainer,
                "outline" to outline,
                "outlineVariant" to outlineVariant,
                "scrim" to scrim,
                "surfaceBright" to surfaceBright,
                "surfaceDim" to surfaceDim,
                "surfaceContainer" to surfaceContainer,
                "surfaceContainerHigh" to surfaceContainerHigh,
                "surfaceContainerHighest" to surfaceContainerHighest,
                "surfaceContainerLow" to surfaceContainerLow,
                "surfaceContainerLowest" to surfaceContainerLowest,
                "primaryFixed" to primaryFixed,
                "primaryFixedDim" to primaryFixedDim,
                "onPrimaryFixed" to onPrimaryFixed,
                "onPrimaryFixedVariant" to onPrimaryFixedVariant,
                "secondaryFixed" to secondaryFixed,
                "secondaryFixedDim" to secondaryFixedDim,
                "onSecondaryFixed" to onSecondaryFixed,
                "onSecondaryFixedVariant" to onSecondaryFixedVariant,
                "tertiaryFixed" to tertiaryFixed,
                "tertiaryFixedDim" to tertiaryFixedDim,
                "onTertiaryFixed" to onTertiaryFixed,
                "onTertiaryFixedVariant" to onTertiaryFixedVariant,
            )

        // Written out rather than `String.format`, which is a JVM extension: this file compiles for
        // `iosArm64` too.
        fun hex(color: Color) =
            "#" + color.toArgb().toUInt().toString(16).uppercase().padStart(8, '0')

        /** WCAG, on the relative luminance Compose already computes. */
        fun contrastRatio(a: Color, b: Color): Double {
            val first = a.luminance().toDouble()
            val second = b.luminance().toDouble()
            return (max(first, second) + 0.05) / (min(first, second) + 0.05)
        }
    }
}
