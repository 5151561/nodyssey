package io.github.nodyssey.ui.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPanelTest {
    @Test
    fun `bundles all three NodeSeek sticker groups`() {
        assertEquals(listOf(149, 22, 32), NodeSeekEmojiGroups.take(3).map { it.entries.size })

        val stickers = NodeSeekEmojiGroups
            .take(3)
            .flatMap(EmojiGroup::entries)
            .filterIsInstance<EmojiEntry.Sticker>()

        assertEquals(203, stickers.size)
        assertEquals(203, stickers.map { it.shortcode }.distinct().size)
        assertTrue(stickers.all { it.assetPath.startsWith("file:///android_asset/stickers/") })
    }

    @Test
    fun `inserts the same shortcode as the site editor`() {
        val first = NodeSeekEmojiGroups.first().entries.first() as EmojiEntry.Sticker

        assertEquals("ac01", first.name)
        assertEquals(" :ac01: ", first.insertion)
        assertEquals("file:///android_asset/stickers/ac/01.png", first.assetPath)
    }
}
