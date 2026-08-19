package io.github.plaza.core.image

import coil3.svg.Svg
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompatSvgParserTest {
    /**
     * The regression this exists for, stated as the difference it makes: the same bytes that the
     * stock parser refuses parse fine through ours. A `Check.Place` report is a character grid, so
     * `ch` is on the root and on every row in the file, and AndroidSVG treats a unit it cannot name
     * as fatal — one attribute is enough to lose the whole picture.
     */
    @Test
    fun `a report the stock parser refuses parses through the compat one`() {
        val report = fixture("check-place-ip-report.svg")

        val stock = runCatching { Svg.Parser.DEFAULT.parse(Buffer().writeUtf8(report)) }
        assertTrue("The stock parser was expected to fail on `ch`", stock.isFailure)

        val svg = CompatSvgParser().parse(Buffer().writeUtf8(report))
        assertTrue("Rewritten document should have a width", svg.width > 0f)
    }

    /**
     * The height comes back exact because `47em` at the document's own `font-size: 14px` is 658px and
     * nothing about the font enters into it. The width is `74ch`, which does depend on the font being
     * measured, so it is only asserted to be in the neighbourhood of 74 characters.
     */
    @Test
    fun `the document keeps the size the report was laid out at`() {
        val svg = CompatSvgParser().parse(Buffer().writeUtf8(fixture("check-place-ip-report.svg")))

        assertEquals(658f, svg.height, 0.5f)
        assertTrue("Unexpected width ${svg.width}", svg.width in 500f..700f)
    }

    @Test
    fun `ch lengths become em, which AndroidSVG resolves against the document's own font size`() {
        val rewritten =
            rewriteForAndroidSvg(
                """<svg width="10ch" height="2em"><rect x="1.5ch" y="0em" width="6ch"/></svg>""",
                GridMetrics,
            )

        assertNotNull(rewritten)
        assertTrue(rewritten!!, rewritten.contains("""x="0.9em""""))
        assertTrue(rewritten, rewritten.contains("""width="3.6em""""))
    }

    /**
     * The root is the one place the rewrite has to commit to pixels: AndroidSVG answers -1 when asked
     * how wide a font-relative document is, and an SVG with neither an intrinsic size nor a viewBox
     * gives Coil nothing to scale.
     */
    @Test
    fun `the root gets an absolute size, taken from the stylesheet's font size`() {
        val rewritten =
            rewriteForAndroidSvg(
                """<svg width="10ch" height="2em"><style>* { font-size: 14px; }</style></svg>""",
                GridMetrics,
            )

        assertTrue(rewritten!!, rewritten.contains("""width="84px""""))
        assertTrue(rewritten, rewritten.contains("""height="28px""""))
    }

    /** A `ch` in what the report *says* is text, not a length. Rewriting it would corrupt the report. */
    @Test
    fun `ch in text content is left alone`() {
        val rewritten =
            rewriteForAndroidSvg(
                """<svg width="10ch"><text x="0ch"><tspan>disk 5ch queue</tspan></text></svg>""",
                GridMetrics,
            )

        assertTrue(rewritten!!, rewritten.contains("disk 5ch queue"))
        assertTrue(rewritten, rewritten.contains("""x="0em""""))
    }

    @Test
    fun `text is dropped to its centre only where the document asked for a central baseline`() {
        val centred =
            rewriteForAndroidSvg(
                """<svg width="1ch"><style>text { dominant-baseline: central; }</style><text y="1em">a</text></svg>""",
                GridMetrics,
            )
        assertTrue(centred!!, centred.contains("""<text dy="0.35em" y="1em">"""))

        val plain =
            rewriteForAndroidSvg(
                """<svg width="1ch"><text y="1em">a</text></svg>""",
                GridMetrics,
            )
        assertTrue(plain!!, plain.contains("""<text y="1em">"""))
    }

    /**
     * The report says it is 10 columns wide; drawn in a font where `#` takes two columns, its one
     * line needs 20. Sizing to the declaration would clip half the line off the right edge, which is
     * what 网络质量's Braille sparklines did.
     */
    @Test
    fun `the root widens to fit a line whose glyphs are wider than their columns`() {
        val rewritten =
            rewriteForAndroidSvg(
                """<svg width="10ch" height="2em"><style>* { font-size: 10px; }</style>""" +
                    """<text x="0ch"><tspan>##########</tspan></text></svg>""",
                WideGlyphMetrics,
            )

        assertTrue(rewritten!!, rewritten.contains("""width="200px""""))
        assertTrue("height must not follow the width", rewritten.contains("""height="20px""""))
    }

    /** Null means "this one is already fine" — the caller then hands the parser the original bytes. */
    @Test
    fun `an SVG AndroidSVG already understands is not rewritten`() {
        assertNull(
            rewriteForAndroidSvg(
                """<svg width="100" height="100" viewBox="0 0 100 100"><circle cx="50" cy="50" r="40"/></svg>""",
                GridMetrics,
            ),
        )
    }

    /** A font in which the grid is exactly a grid: every character one `ch` wide. */
    private object GridMetrics : SvgTextMetrics {
        override val chWidthEm = 0.6f
        override val centralBaselineDyEm = 0.35f

        override fun widthOf(text: String, fontSizePx: Float) = text.length * chWidthEm * fontSizePx
    }

    /** A font where one glyph is wider than its column — Braille and box drawing on Android. */
    private object WideGlyphMetrics : SvgTextMetrics {
        override val chWidthEm = GridMetrics.chWidthEm
        override val centralBaselineDyEm = GridMetrics.centralBaselineDyEm

        override fun widthOf(text: String, fontSizePx: Float) =
            text.sumOf { if (it == '#') 2.0 else chWidthEm.toDouble() }.toFloat() * fontSizePx
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture: $name"
        }.bufferedReader().use { it.readText() }
}
