package io.github.plaza.core.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgMarkupTest {

    /** The cartoon avatars NodeSeek generates: shapes and nothing else, and Skia draws them fine. */
    @Test
    fun `a document of shapes needs no other renderer`() {
        val markup =
            """<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="40" fill="#f90"/></svg>"""

        assertFalse(SvgMarkup.needsFullRenderer(markup))
    }

    /** An IP card, a shields.io badge, a 测评 terminal report: all of them are text. */
    @Test
    fun `text sends the document to the other renderer`() {
        val markup = """<svg viewBox="0 0 720 340"><text x="65" y="46">访客你好</text></svg>"""

        assertTrue(SvgMarkup.needsFullRenderer(markup))
    }

    @Test
    fun `a bitmap embedded as a data uri counts too`() {
        val markup = """<svg viewBox="0 0 720 340"><image href="data:image/png;base64,iVBOR"/></svg>"""

        assertTrue(SvgMarkup.needsFullRenderer(markup))
    }

    @Test
    fun `so does html smuggled in through a foreign object`() {
        val markup = """<svg viewBox="0 0 10 10"><foreignObject><p>hi</p></foreignObject></svg>"""

        assertTrue(SvgMarkup.needsFullRenderer(markup))
    }

    /** Case is the document author's business, not ours. */
    @Test
    fun `the element names are matched whatever their case`() {
        assertTrue(SvgMarkup.needsFullRenderer("""<svg><TEXT x="0">hi</TEXT></svg>"""))
    }

    @Test
    fun `the view box gives the size`() {
        val size = SvgMarkup.intrinsicSize("""<svg viewBox="0 0 720 340" width="100%"></svg>""")

        assertEquals(SvgSize(720f, 340f), size)
    }

    /** Commas are as legal a separator as spaces, and both turn up in the wild. */
    @Test
    fun `a comma separated view box reads the same`() {
        val size = SvgMarkup.intrinsicSize("""<svg viewBox="0,0,48,24"></svg>""")

        assertEquals(SvgSize(48f, 24f), size)
    }

    @Test
    fun `width and height answer when there is no view box`() {
        val size = SvgMarkup.intrinsicSize("""<svg width="120px" height="60" xmlns="…"></svg>""")

        assertEquals(SvgSize(120f, 60f), size)
    }

    /**
     * The case the null exists for: a percentage resolves against a page this is not on, and an
     * invented answer would be an image drawn at the wrong size rather than one drawn by Skia.
     */
    @Test
    fun `a document sized only in percentages has no answer`() {
        assertNull(SvgMarkup.intrinsicSize("""<svg width="100%" height="100%"></svg>"""))
    }

    @Test
    fun `a document with no root tag has no answer either`() {
        assertNull(SvgMarkup.intrinsicSize("<html><body>not an svg</body></html>"))
    }

    /** A zero-sized document is no more drawable than an unmeasurable one. */
    @Test
    fun `a zero view box is refused`() {
        assertNull(SvgMarkup.intrinsicSize("""<svg viewBox="0 0 0 0"></svg>"""))
    }

    @Test
    fun `a size fits into a box by whichever side runs out first`() {
        val fitted = SvgSize(720f, 340f).fitInside(width = 360, height = 1000)

        assertEquals(SvgSize(360f, 170f), fitted)
    }

    @Test
    fun `a box with one side open is decided by the other`() {
        val fitted = SvgSize(720f, 340f).fitInside(width = null, height = 170)

        assertEquals(SvgSize(360f, 170f), fitted)
    }

    /** Vector, so a request for more than the document's own size is a request to draw it bigger. */
    @Test
    fun `a bigger box scales the document up`() {
        val fitted = SvgSize(100f, 50f).fitInside(width = 300, height = null)

        assertEquals(SvgSize(300f, 150f), fitted)
    }

    /** A ceiling is not a request: the document stays its own size until it is over the limit. */
    @Test
    fun `a ceiling larger than the document leaves it alone`() {
        val original = SvgSize(720f, 340f)

        assertEquals(original, original.atMost(width = 4096, height = 4096))
    }

    @Test
    fun `a ceiling smaller than the document shrinks it`() {
        val shrunk = SvgSize(720f, 340f).atMost(width = 360, height = 4096)

        assertEquals(SvgSize(360f, 170f), shrunk)
    }

    @Test
    fun `an unconstrained box leaves the size alone`() {
        val original = SvgSize(720f, 340f)

        assertEquals(original, original.fitInside(width = null, height = null))
    }

    /** The attributes the badges carry are on the root beside the ones being read. */
    @Test
    fun `the size is read off a root tag carrying other attributes`() {
        val markup =
            """<svg xmlns="http://www.w3.org/2000/svg" width="104" height="20" role="img">""" +
                """<text x="5" y="14">build</text></svg>"""

        assertEquals(SvgSize(104f, 20f), SvgMarkup.intrinsicSize(markup))
        assertTrue(SvgMarkup.needsFullRenderer(markup))
    }
}
