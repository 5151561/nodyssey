package io.github.nodyssey.data.sticker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StickerGroupLayoutTest {
    @Test
    fun `a group the stored order has never seen goes after the group it follows by default`() {
        val layout = StickerGroupLayout(order = listOf("site:ac", "mine", "gh:o/r/a"))

        val arranged = layout.arrange(listOf("mine", "gh:o/r/a", "gh:o/r/b", "site:ac"))

        assertEquals(listOf("site:ac", "mine", "gh:o/r/a", "gh:o/r/b"), arranged)
    }

    @Test
    fun `stored keys for groups that are gone are dropped and a new first group stays first`() {
        val layout = StickerGroupLayout(order = listOf("site:ac", "gh:o/old/a"))

        assertEquals(listOf("mine", "site:ac"), layout.arrange(listOf("mine", "site:ac")))
    }

    @Test
    fun `a folder key gives back its repository and a path with slashes in it`() {
        assertEquals("o/r" to "packs/熊猫头", parseFolderGroupKey(folderGroupKey("o/r", "packs/熊猫头")))
        assertNull(parseFolderGroupKey("site:ac"))
    }
}
