package io.github.nodyssey.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 识别剪贴板中的帖子: which clipboard text earns a prompt, and that one link earns it once. */
class ClipboardPostLinkTest {
    @Test
    fun `a thread link is offered in its canonical spelling`() {
        val offer = clipboardPostToOffer("http://nodeseek.com/post-123456-2#7", lastOffered = null)

        assertEquals(ClipboardPost("https://www.nodeseek.com/post-123456-2", 123456, 2), offer)
    }

    @Test
    fun `a thread link inside a sentence is found`() {
        val offer = clipboardPostToOffer("看看这个 https://www.nodeseek.com/post-42-1。", lastOffered = null)

        assertEquals(42L, offer?.postId)
    }

    @Test
    fun `another site's link is not offered`() {
        assertNull(clipboardPostToOffer("https://example.com/post-42-1", lastOffered = null))
    }

    @Test
    fun `a link to this site that is not a thread is not offered`() {
        assertNull(clipboardPostToOffer("https://www.nodeseek.com/space/5230", lastOffered = null))
    }

    @Test
    fun `text without a link is not offered`() {
        assertNull(clipboardPostToOffer("post-42-1", lastOffered = null))
    }

    @Test
    fun `the same thread copied twice is offered once`() {
        var lastOffered: String? = null
        val offers =
            listOf("https://www.nodeseek.com/post-42-1", "https://nodeseek.com/post-42-1#3").mapNotNull { text ->
                clipboardPostToOffer(text, lastOffered)?.also { lastOffered = it.url }
            }

        assertEquals(1, offers.size)
    }

    @Test
    fun `a different thread after an offered one is offered too`() {
        val offer =
            clipboardPostToOffer("https://www.nodeseek.com/post-43-1", lastOffered = "https://www.nodeseek.com/post-42-1")

        assertEquals(43L, offer?.postId)
    }
}
