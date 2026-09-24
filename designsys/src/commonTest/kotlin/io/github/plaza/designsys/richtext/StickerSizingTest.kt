package io.github.plaza.designsys.richtext

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

/** What [StickerSizeCache] will and will not remember of a decoded sticker. */
class StickerSizingTest {
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
