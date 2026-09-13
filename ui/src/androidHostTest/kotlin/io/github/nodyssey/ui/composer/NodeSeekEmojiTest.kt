package io.github.nodyssey.ui.composer

import io.github.nodyssey.core.NodeSeekStickers
import io.github.plaza.designsys.editor.EmojiEntry
import io.github.plaza.designsys.editor.EmojiGroup
import io.github.plaza.designsys.editor.insertion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSeekEmojiTest {
    /**
     * Four tabs, not five. The site's own editor has a fifth labelled APP, but it inserts 投票 and
     * 星辰收款 rather than stickers — this app used to read it as a sticker group and fill it with
     * eighteen Unicode emoji the site does not offer.
     */
    @Test
    fun `offers all three NodeSeek sticker groups`() {
        assertEquals(4, NodeSeekEmojiGroups.size)
        assertEquals(listOf(149, 22, 32), NodeSeekEmojiGroups.take(3).map { it.entries.size })

        val stickers = NodeSeekEmojiGroups
            .take(3)
            .flatMap(EmojiGroup::entries)
            .filterIsInstance<EmojiEntry.Sticker>()

        assertEquals(203, stickers.size)
        assertEquals(203, stickers.map { it.shortcode }.distinct().size)
        // The same prefix RichContentParser recognises as an inline sticker, so the panel and the
        // thread share one cached copy instead of the panel carrying its own in the APK.
        assertTrue(
            stickers.all {
                it.url.startsWith("https://www.nodeseek.com/static/image/sticker/")
            },
        )
    }

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
