package io.github.plaza.designsys.richtext

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind 表情统一缩限, away from any renderer.
 *
 * Everything here is decided before a single pixel is drawn, because an inline placeholder has to
 * declare its box up front — so this is the whole of what the setting does.
 */
class StickerSizingTest {
    private val density = Density(density = 2f, fontScale = 1f)

    @Test
    fun `uniform sizing squares every sticker whatever shape it is`() {
        val sizing = StickerSizing(uniform = true, uniformSize = 32.sp)
        val wide = sizing.boxSize(IntSize(width = 240, height = 60), density)
        val tall = sizing.boxSize(IntSize(width = 60, height = 240), density)

        // sp becomes dp through the font scale alone, so 32sp is 32dp at the default scale.
        assertEquals(32.dp, wide.width)
        assertEquals(32.dp, wide.height)
        assertEquals(tall, wide)
    }

    /**
     * The mode's whole point: it costs nothing to lay out a sticker nobody has decoded yet, which is
     * why turning 统一缩限 on is also how you turn off the first-sight reflow.
     */
    @Test
    fun `uniform sizing needs no measurement`() {
        val sizing = StickerSizing(uniform = true, uniformSize = 20.sp)

        assertEquals(sizing.boxSize(IntSize(80, 80), density), sizing.boxSize(null, density))
    }

    /**
     * A CSS pixel is a dp, which is the rule the site's own layout follows — so the file's pixels
     * become dp untouched, and the screen's density does not enter into it. Pinned at two densities
     * because dividing by density is the bug this mode shipped with: it made a sticker four times
     * smaller on a 4x phone than on a 1x one, when the site draws it the same size on both.
     */
    @Test
    fun `natural sizing reads the sticker's own pixels as dp`() {
        val sizing = StickerSizing(uniform = false)
        val pixels = IntSize(width = 64, height = 48)

        val box = sizing.boxSize(pixels, density)

        assertEquals(64.dp, box.width)
        assertEquals(48.dp, box.height)
        assertEquals(box, sizing.boxSize(pixels, Density(density = 4f, fontScale = 1f)))
    }

    /** `img.sticker { max-width: 90px }`: the width is clamped and the height follows it down. */
    @Test
    fun `natural sizing clamps a wide sticker the way the site's css does`() {
        val sizing = StickerSizing(uniform = false)

        val box = sizing.boxSize(IntSize(width = 360, height = 120), density)

        assertEquals(NATURAL_STICKER_MAX_WIDTH, box.width)
        assertEquals(30.dp, box.height)
    }

    /**
     * Nothing has decoded it yet, so there is no natural size to use. It has to be the 20sp box
     * rather than nothing: a sticker with no box at all is a gap in the line that reads as a space.
     */
    @Test
    fun `natural sizing falls back to the inline box until the pixels arrive`() {
        val box = StickerSizing(uniform = false).boxSize(null, density)

        assertEquals(with(density) { DEFAULT_STICKER_SIZE.toDp() }, box.width)
        assertEquals(box.width, box.height)
    }

    @Test
    fun `the cache refuses a size no layout could use`() {
        StickerSizeCache.clear()

        StickerSizeCache.record(URL, width = 0, height = 40)

        assertEquals(null, StickerSizeCache.naturalSize(URL))
    }

    @Test
    fun `the cache remembers what decoded`() {
        StickerSizeCache.clear()

        StickerSizeCache.record(URL, width = 64, height = 48)

        assertEquals(IntSize(64, 48), StickerSizeCache.naturalSize(URL))
        StickerSizeCache.clear()
    }
}

private const val URL = "https://example.invalid/sticker/ac/01.png"
