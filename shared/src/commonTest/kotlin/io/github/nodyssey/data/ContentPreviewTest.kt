package io.github.nodyssey.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one line a notification row shows instead of sending the reader into the thread.
 *
 * Each case here is a shape the site actually writes: the 回复 button's address line, the 引用
 * button's blockquote, an emoji panel sticker, and a body whose content is a picture and nothing
 * else. What they have in common is that flattening them naively produces a line that says nothing —
 * `@me #3`, or the reader's own words quoted back at them.
 */
class ContentPreviewTest {
    @Test
    fun `nothing to preview stays nothing`() {
        assertEquals(emptyList(), contentPreview(null))
        assertEquals(emptyList(), contentPreview("   "))
    }

    @Test
    fun `plain words come through as one run`() {
        assertEquals(listOf(PreviewPart.Text("还没有这个功能")), contentPreview("还没有这个功能"))
    }

    /** `@name [#7](…)` is what the site's 回复 button writes; the row already says all of that. */
    @Test
    fun `drops the address the reply button wrote`() {
        val parts = contentPreview("@nssk [#7](/post-703863-1#7) 还没有这个功能")

        assertEquals(listOf(PreviewPart.Text("还没有这个功能")), parts)
    }

    /** A reply that only @-s, with no floor behind it, loses the same opening. */
    @Test
    fun `drops a bare leading mention`() {
        assertEquals(listOf(PreviewPart.Text("收到")), contentPreview("@nssk 收到"))
    }

    /**
     * …and not a name the writer ran straight into their sentence.
     *
     * Chinese does not space its words, so a greedy name would be read as the whole line and the
     * preview would come out empty — which is why the separator after the name is required.
     */
    @Test
    fun `keeps a leading mention that has no separator after it`() {
        assertEquals(listOf(PreviewPart.Text("@某人你好")), contentPreview("@某人你好"))
    }

    /** …but not one in the middle, which is somebody the writer chose to address. */
    @Test
    fun `keeps a mention that is not the opening`() {
        val parts = contentPreview("这个问题 @nssk 应该清楚")

        assertEquals(listOf(PreviewPart.Text("这个问题 @nssk 应该清楚")), parts)
    }

    /** The 引用 button quotes what it answers — which, in a notification about my comment, is me. */
    @Test
    fun `drops the quotation and keeps the answer`() {
        val parts = contentPreview("> @me [#3](/post-1-1#3)\n> 请问怎么改用户名\n\n设置里就能改")

        assertEquals(listOf(PreviewPart.Text("设置里就能改")), parts)
    }

    /** Unless the quote was the whole reply, in which case it is all there is to show. */
    @Test
    fun `a reply that is only a quotation still shows it`() {
        assertEquals(listOf(PreviewPart.Text("请问怎么改用户名")), contentPreview("> 请问怎么改用户名"))
    }

    @Test
    fun `names the pictures rather than dropping them`() {
        val sticker = contentPreview("![](https://www.nodeseek.com/static/image/sticker/ac/01.png)")
        val photo = contentPreview("![](https://img.example.com/screenshot.png)")

        assertEquals(listOf(PreviewPart.Placeholder(PreviewPlaceholder.STICKER)), sticker)
        assertEquals(listOf(PreviewPart.Placeholder(PreviewPlaceholder.IMAGE)), photo)
    }

    @Test
    fun `names a code block and a table too`() {
        assertEquals(
            listOf(PreviewPart.Placeholder(PreviewPlaceholder.CODE)),
            contentPreview("```sh\nsudo reboot\n```"),
        )
        assertEquals(
            listOf(PreviewPart.Placeholder(PreviewPlaceholder.TABLE)),
            contentPreview("|区域|价格|\n|---|---|\n|日本|9.9|"),
        )
    }

    /** Line breaks become spaces: the row is one line, and a gap in it reads as a bug. */
    @Test
    fun `runs the paragraphs together on one line`() {
        val parts = contentPreview("第一段\n\n第二段")

        assertEquals(listOf(PreviewPart.Text("第一段 第二段")), parts)
    }

    /** The site's own JSON is inconsistent about which it sends, so both have to work. */
    @Test
    fun `reads rendered markup as well as markdown`() {
        val parts = contentPreview("""<p>还没有这个功能<img class="sticker" src="/static/image/sticker/ac/01.png"></p>""")

        assertEquals(
            listOf(
                PreviewPart.Text("还没有这个功能"),
                PreviewPart.Placeholder(PreviewPlaceholder.STICKER),
            ),
            parts,
        )
    }

    /** A wall of text is cut where the row could not have shown more of it anyway. */
    @Test
    fun `stops at the limit and says so`() {
        val parts = contentPreview("字".repeat(400))

        val text = (parts.single() as PreviewPart.Text).text
        assertTrue(text.length <= PREVIEW_LIMIT + 1, "preview kept ${text.length} characters")
        assertTrue(text.endsWith("…"), text)
    }
}
