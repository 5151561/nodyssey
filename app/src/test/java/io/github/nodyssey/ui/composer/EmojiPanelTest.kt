package io.github.nodyssey.ui.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPanelTest {
    @Test
    fun `offers all three NodeSeek sticker groups`() {
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
}
