package io.github.plaza.designsys.richtext

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A quote chip is drawn as a background behind its label, so a label the line breaker splits comes
 * out as two pills — and the second one has no padding to keep its round cap off the glyph that
 * opens the line. See [unbreakable].
 */
class UnbreakableLabelTest {
    private val joiner = '⁠'

    @Test
    fun `joins every gap between hanzi, which break anywhere`() {
        assertEquals("腿${joiner}王${joiner}的", "腿王的".unbreakable())
    }

    @Test
    fun `leaves a single character alone`() {
        assertEquals("王", "王".unbreakable())
        assertEquals("", "".unbreakable())
    }

    /** Splitting a ZWJ sequence would draw the family as the three people it is made of. */
    @Test
    fun `never joins inside one emoji`() {
        val family = "👨‍👩‍👧"
        val heart = "❤️"
        assertEquals(family, family.unbreakable())
        assertEquals(heart, heart.unbreakable())
        assertEquals("$family$joiner$heart", (family + heart).unbreakable())
    }

    /** The whole point: what is joined must still read as the same text once the joiners are gone. */
    @Test
    fun `adds nothing but joiners`() {
        val label = "@🐔腿王🍗 #128"
        val joined = label.unbreakable()

        assertTrue(joined.contains(joiner))
        assertEquals(label, joined.filterNot { it == joiner })
    }
}
