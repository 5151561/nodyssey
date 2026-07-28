package io.github.nodyssey.ui.composer

import io.github.nodyssey.model.InlineNode
import io.github.nodyssey.model.RichNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreviewTest {
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
}
