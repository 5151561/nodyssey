package io.github.nodyssey.ui.composer

import io.github.nodyssey.core.NodeSeekStickers
import io.github.plaza.designsys.editor.EmojiEntry
import io.github.plaza.designsys.editor.EmojiGroup
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
