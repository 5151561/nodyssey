package io.github.plaza.designsys.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What a seed colour is allowed to produce.
 *
 * The generator is a library call, so these are not tests of the algorithm — they are the guard on
 * the contract the app leans on: that every text/background pair it hands `MaterialTheme` is
 * readable, that light and dark are actually different, and that the seed reaches the result. A
 * library bump that changed the default variant or spec would land here rather than on a reader's
 * screen.
 */
class SeedColorSchemeTest {
    @Test
    fun `every ink sits legibly on the surface it is drawn on`() {
        for (seed in Seeds) {
            for (dark in listOf(false, true)) {
                val scheme = plazaSeedColorScheme(seed, dark)
                val pairs =
                    listOf(
                        "onSurface" to (scheme.onSurface to scheme.surface),
                        "onSurfaceVariant" to (scheme.onSurfaceVariant to scheme.surface),
                        "onPrimary" to (scheme.onPrimary to scheme.primary),
                        "onPrimaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                        "onSecondaryContainer" to
                            (scheme.onSecondaryContainer to scheme.secondaryContainer),
                        "onTertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                        "onError" to (scheme.onError to scheme.error),
                    )
                for ((role, pair) in pairs) {
                    val ratio = contrastRatio(pair.first, pair.second)
                    assertTrue(
                        "$role on ${hex(seed)} ${if (dark) "dark" else "light"} is $ratio:1",
                        ratio >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun `light and dark are different schemes`() {
        for (seed in Seeds) {
            val light = plazaSeedColorScheme(seed, darkTheme = false)
            val dark = plazaSeedColorScheme(seed, darkTheme = true)
            assertNotEquals(hex(seed), light.surface.toArgb().toLong(), dark.surface.toArgb().toLong())
            assertTrue(hex(seed), light.surface.luminance() > dark.surface.luminance())
        }
    }

    @Test
    fun `the seed reaches the scheme`() {
        val teal = plazaSeedColorScheme(Color(0xFF35606E), darkTheme = false)
        val amber = plazaSeedColorScheme(Color(0xFF8A5100), darkTheme = false)
        assertNotEquals(teal.primary.toArgb(), amber.primary.toArgb())
        // Same family as the seed, not some unrelated hue.
        assertEquals(
            Color(0xFF35606E).toPlazaSeedHct().hue.toDouble(),
            teal.primary.toPlazaSeedHct().hue.toDouble(),
            12.0,
        )
    }

    @Test
    fun `the seed's own lightness barely reaches the scheme`() {
        // The reason the picker moves 色相 and 鲜艳度 rather than lightness: the palettes are built
        // from the seed's hue and chroma, and its tone is thrown away. Not bit-exact — rounding a
        // seed to eight bits per channel shifts its hue and chroma by a fraction of a unit, which
        // can move a role by one step — so the property is stated as "the same colour", not "the
        // same integer". Tones near black or white are excluded on purpose: there the chroma asked
        // for does not fit in sRGB and gets clipped, which really does produce a different seed.
        val base = Color(0xFF35606E).toPlazaSeedHct()
        val reference = plazaSeedColorScheme(base.copy(tone = 40f).toColor(), darkTheme = false)
        for (tone in listOf(20f, 30f, 50f, 60f, 70f, 80f)) {
            val variant = base.copy(tone = tone).toColor()
            assertNotEquals(base.toColor().toArgb().toLong(), variant.toArgb().toLong())
            val primary = plazaSeedColorScheme(variant, darkTheme = false).primary
            for (channel in listOf(16, 8, 0)) {
                val expected = (reference.primary.toArgb() shr channel) and 0xFF
                val actual = (primary.toArgb() shr channel) and 0xFF
                assertTrue("tone $tone -> ${hex(primary)}", abs(expected - actual) <= 3)
            }
        }
    }

    @Test
    fun `a colour survives the trip through the picker's coordinates`() {
        for (seed in Seeds) {
            val roundTripped = seed.toPlazaSeedHct().toColor()
            // HCT is a perceptual space and sRGB is not, so a channel may land a step away.
            for (channel in listOf(16, 8, 0)) {
                val before = (seed.toArgb() shr channel) and 0xFF
                val after = (roundTripped.toArgb() shr channel) and 0xFF
                assertTrue("${hex(seed)} -> ${hex(roundTripped)}", abs(before - after) <= 2)
            }
        }
    }

    private companion object {
        /** The nine the settings screen offers, plus the extremes a hand-picked colour can reach. */
        val Seeds =
            listOf(
                Color(0xFF35606E),
                Color(0xFF00639B),
                Color(0xFF6750A4),
                Color(0xFF9C4472),
                Color(0xFF9C4234),
                Color(0xFF8A5100),
                Color(0xFF5F6B2E),
                Color(0xFF2E6B4F),
                Color(0xFF5B5F66),
                Color(0xFF000000),
                Color(0xFFFFFFFF),
                Color(0xFFFF0000),
                Color(0xFF00FF00),
            )

        fun hex(color: Color) = String.format("#%08X", color.toArgb())

        /** WCAG, on the relative luminance Compose already computes. */
        fun contrastRatio(a: Color, b: Color): Double {
            val first = a.luminance().toDouble()
            val second = b.luminance().toDouble()
            return (max(first, second) + 0.05) / (min(first, second) + 0.05)
        }
    }
}
