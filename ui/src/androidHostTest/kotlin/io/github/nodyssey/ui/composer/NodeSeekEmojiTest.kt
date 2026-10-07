package io.github.nodyssey.ui.composer

import io.github.nodyssey.core.NodeSeekStickers
import io.github.plaza.designsys.editor.EmojiEntry
import io.github.plaza.designsys.editor.EmojiGroup
import io.github.plaza.designsys.editor.InlinePicture
import io.github.plaza.designsys.editor.insertion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSeekEmojiTest {
    @Test
    fun `inserts the same shortcode as the site editor`() {
        val first = NodeSeekEmojiGroups.first().entries.first() as EmojiEntry.Sticker

        assertEquals("ac01", first.name)
        assertEquals(" :ac01: ", first.insertion)
        assertEquals("https://www.nodeseek.com/static/image/sticker/ac/01.png", first.url)
    }

    /**
     * The round trip the thread depends on: what the panel inserts, the renderer has to resolve back
     * to the very picture the panel showed. Before [NodeSeekStickers] the catalogue lived only here,
     * so a sticker could be sent and then displayed as `:ac01:` to the person who sent it.
     */
    @Test
    fun `every sticker the panel offers resolves back to its own picture`() {
        val stickers = NodeSeekEmojiGroups
            .take(3)
            .flatMap(EmojiGroup::entries)
            .filterIsInstance<EmojiEntry.Sticker>()

        assertTrue(stickers.all { NodeSeekStickers.urlFor(it.name) == it.url })
        // And nothing beyond it: a name the site has never shipped has to stay text, or a message
        // reading "8:30:" would draw a broken image where the time was.
        assertEquals(null, NodeSeekStickers.urlFor("ac99"))
        assertEquals(null, NodeSeekStickers.urlFor("30"))
    }
}

class FindStickersTest {
    private val mine = "https://cdn.example/pack/doge.gif"

    private fun find(text: String) = findStickers(text, { it == mine }, NodeSeekStickers::urlFor)

    @Test
    fun `a saved sticker becomes a picture and an uploaded photo stays its link`() {
        val text = "看 ![doge]($mine) 和 ![截图](https://i.example/shot.png)"

        assertEquals(listOf(InlinePicture(2, 2 + "![doge]($mine)".length, mine)), find(text))
    }

    /** The scan has to restart at every colon, or the `:` closing a time eats the one opening a sticker. */
    @Test
    fun `a site shortcode after a stray colon is still found`() {
        val text = "8:30 :ac01:"

        assertEquals(listOf(InlinePicture(5, 11, NodeSeekStickers.urlFor("ac01")!!)), find(text))
    }

    @Test
    fun `a shortcode the site does not have stays text`() {
        assertEquals(emptyList<InlinePicture>(), find(":ac99: 21:30:"))
    }

    /** The post sends code as written, so the editor must not hide what is typed inside it. */
    @Test
    fun `stickers inside a fenced block or a backtick span stay text`() {
        val text = "```\n:ac01: ![doge]($mine)\n```\n`:ac01:` :ac01:"

        assertEquals(listOf(InlinePicture(text.length - 6, text.length, NodeSeekStickers.urlFor("ac01")!!)), find(text))
    }

    @Test
    fun `an unclosed fence runs to the end and a lone backtick is just a character`() {
        assertEquals(emptyList<InlinePicture>(), find("```\n:ac01:"))
        assertEquals(1, find("a ` b :ac01:").size)
    }
}
