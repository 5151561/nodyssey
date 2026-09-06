package io.github.nodyssey.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one line the 私信 list shows for the message a conversation is sitting on.
 *
 * Each case here is a shape the site actually writes: the bubble's 引用 blockquote, an emoji panel
 * sticker, and a body whose content is a picture and nothing else. What they have in common is that
 * flattening them naively produces a line that says nothing — a `![](…png)` printed as written, or
 * the reader's own words quoted back at them.
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

    /** An `@` is whoever the writer chose to address; nothing about it is worth dropping. */
    @Test
    fun `keeps a mention wherever it stands`() {
        assertEquals(listOf(PreviewPart.Text("@nssk 收到")), contentPreview("@nssk 收到"))
        assertEquals(
            listOf(PreviewPart.Text("这个问题 @nssk 应该清楚")),
            contentPreview("这个问题 @nssk 应该清楚"),
        )
    }

    /** The 引用 action quotes what it answers, which is a message the reader has already seen. */
    @Test
    fun `drops the quotation and keeps the answer`() {
        val parts = contentPreview("> 请问怎么改用户名\n\n设置里就能改")

        assertEquals(listOf(PreviewPart.Text("设置里就能改")), parts)
    }

    /** Unless the quote was the whole message, in which case it is all there is to show. */
    @Test
    fun `a message that is only a quotation still shows it`() {
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

    /**
     * One space at a join, never two.
     *
     * A picture on its own line is a block, so the separator between blocks and the space the next
     * block's own text begins with both land after the placeholder — and `[图片]  看这个` reads as a
     * word that failed to render rather than as a gap.
     */
    @Test
    fun `leaves one space between a placeholder and what follows it`() {
        val parts = contentPreview("![](https://img.example.com/1.png) 看这个")

        assertEquals(
            listOf(
                PreviewPart.Placeholder(PreviewPlaceholder.IMAGE),
                PreviewPart.Text(" 看这个"),
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
