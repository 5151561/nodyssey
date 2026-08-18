package io.github.plaza.core.richtext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTest {
    @Test
    fun `parses the editor syntax used by the preview`() {
        val nodes = parseMarkdown(
            """
            ## 标题

            正文有 **加粗** 和 `code`。

            - 第一项
            - 第二项

            ```bash
            echo ok
            ```
            """.trimIndent(),
        )

        assertTrue(nodes[0] is RichNode.Heading)
        val paragraph = nodes[1] as RichNode.Paragraph
        assertTrue(paragraph.inlines.filterIsInstance<InlineNode.Text>().any { it.style.bold })
        assertTrue(paragraph.inlines.filterIsInstance<InlineNode.Text>().any { it.style.code })
        assertEquals(2, (nodes[2] as RichNode.ListBlock).items.size)
        assertEquals("echo ok", (nodes[3] as RichNode.CodeBlock).code)
    }

    @Test
    fun `turns a standalone markdown image into a block image`() {
        val image = parseMarkdown("![截图](https://example.com/a.png)").single() as RichNode.BlockImage

        assertEquals("截图", image.alt)
        assertEquals("https://example.com/a.png", image.url)
    }

    /** An image inside a sentence survives as an image node; the renderer decides how to draw it. */
    @Test
    fun `keeps an image inside a sentence as an inline image`() {
        val paragraph =
            parseMarkdown("徽章 ![runs](https://example.com/b.svg) 在行内").single() as RichNode.Paragraph

        val image = paragraph.inlines.filterIsInstance<InlineNode.Image>().single()
        assertEquals("https://example.com/b.svg", image.url)
        assertEquals("runs", image.alt)
    }

    /** A table cell has no block position, so its images must survive in the inline flow. */
    @Test
    fun `keeps an image inside a table cell`() {
        val table =
            parseMarkdown(
                """
                |结果|说明|
                |---|---|
                |![IPv4](https://example.com/v4.png)|文字|
                """.trimIndent(),
            ).single() as RichNode.Table

        val image = table.content[1][0].filterIsInstance<InlineNode.Image>().single()
        assertEquals("https://example.com/v4.png", image.url)
        assertEquals("IPv4", image.alt)
    }

    /**
     * A space Readme's own shape: an image wrapped in a link, with a title on the link.
     *
     * The title used to be read as part of the URL, and the outer brackets came out as prose.
     */
    @Test
    fun `reads a linked image with a title as a block image`() {
        val image =
            parseMarkdown(
                """[![访客IP信息卡片](https://my.ippurity.com/v1/card)](https://ippurity.com "点击查看IP信息")""",
            ).single() as RichNode.BlockImage

        assertEquals("访客IP信息卡片", image.alt)
        assertEquals("https://my.ippurity.com/v1/card", image.url)
    }

    /**
     * `**[名字](url)**` matched the bold rule first, and the link came out as bold prose.
     *
     * The leading space is the one the readme this came from actually has: up to three of them
     * still open a heading.
     */
    @Test
    fun `keeps a link that is wrapped in emphasis`() {
        val heading = parseMarkdown(" #### **[TG机器人](https://t.me/aurora_scbot)** ||").single() as RichNode.Heading

        val link = heading.inlines.filterIsInstance<InlineNode.Link>().single()
        assertEquals("TG机器人", link.text)
        assertEquals("https://t.me/aurora_scbot", link.url)
        assertTrue(link.style.bold)
        assertEquals(" ||", heading.inlines.filterIsInstance<InlineNode.Text>().joinToString("") { it.text })
    }

    /** Emphasis inside the label reaches the same place, because the model has one style per link. */
    @Test
    fun `keeps emphasis that is inside the link label`() {
        val paragraph = parseMarkdown("[**TG机器人**](https://t.me/aurora_scbot)").single() as RichNode.Paragraph

        val link = paragraph.inlines.single() as InlineNode.Link
        assertEquals("TG机器人", link.text)
        assertTrue(link.style.bold)
    }

    /** An underscore inside a word is part of the word, not an emphasis delimiter. */
    @Test
    fun `leaves an underscore inside a word alone`() {
        val paragraph = parseMarkdown("联系 aurora_scbot_2 就行").single() as RichNode.Paragraph

        assertEquals("联系 aurora_scbot_2 就行", paragraph.inlines.filterIsInstance<InlineNode.Text>().joinToString("") { it.text })
    }

    @Test
    fun `parses a pipe table and keeps the links in its cells`() {
        val table =
            parseMarkdown(
                """
                |区域|价格|报告|
                |---|---:|---|
                |沪日IX|￥10/月|[NQ](https://example.com/r/abc)|
                """.trimIndent(),
            ).single() as RichNode.Table

        assertEquals(2, table.content.size)
        assertEquals("区域", (table.content[0][0].single() as InlineNode.Text).text)
        assertEquals("￥10/月", (table.content[1][1].single() as InlineNode.Text).text)
        val link = table.content[1][2].single() as InlineNode.Link
        assertEquals("NQ", link.text)
        assertEquals("https://example.com/r/abc", link.url)
    }

    /** A line that merely contains a pipe is prose; only an underline of its own width makes a table. */
    @Test
    fun `leaves a paragraph containing a pipe alone`() {
        val nodes = parseMarkdown("屏蔽协议: 【socks】|【http】\n下一行")

        assertTrue(nodes.single() is RichNode.Paragraph, "got $nodes")
    }

    /** A collapsed Readme cut between a table's header and its underline showed raw pipes. */
    @Test
    fun `collapsing never cuts a table in half`() {
        val readme = "介绍\n|区域|价格|\n|---|---|\n|沪日IX|￥10/月|\n|东京|￥20/月|\n尾巴"

        val nodes = parseMarkdown(collapseMarkdown(readme, limit = 2))

        assertEquals("介绍", ((nodes[0] as RichNode.Paragraph).inlines.single() as InlineNode.Text).text)
        assertEquals(3, (nodes[1] as RichNode.Table).content.size)
        assertEquals(2, nodes.size)
    }

    /** The site's readmes write `- #### 标题`, and a list item that only holds inlines shows the hashes. */
    @Test
    fun `parses a heading inside a list item`() {
        val list = parseMarkdown("- #### 支持ipv6转发").single() as RichNode.ListBlock

        val heading = list.items.single().single() as RichNode.Heading
        assertEquals(4, heading.level)
        assertEquals("支持ipv6转发", (heading.inlines.single() as InlineNode.Text).text)
    }
}
