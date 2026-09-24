package io.github.nodyssey.ui.postdetail

import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 复制正文 copies the whole floor, not the excerpt the panel's head shows. */
class FloorTextTest {
    private fun paragraph(vararg inlines: InlineNode) = RichNode.Paragraph(inlines.toList())

    private fun text(value: String) = InlineNode.Text(value)

    @Test
    fun `code, lists, quotes and tables are all copied`() {
        val nodes =
            listOf(
                paragraph(text("跑分如下"), InlineNode.LineBreak, text("第二行")),
                RichNode.CodeBlock(code = "Geekbench 6\nSingle: 1234\n", language = null),
                RichNode.ListBlock(ordered = true, items = listOf(listOf(paragraph(text("装系统"))), listOf(paragraph(text("跑分"))))),
                RichNode.Quote(listOf(paragraph(text("原话")))),
                RichNode.Table(cells = listOf(listOf(listOf(text("CPU")), listOf(text("分数"))), listOf(listOf(text("A")), listOf(text("1"))))),
            )

        assertEquals(
            listOf(
                "跑分如下\n第二行",
                "Geekbench 6\nSingle: 1234",
                "1. 装系统\n2. 跑分",
                "> 原话",
                "CPU\t分数\nA\t1",
            ).joinToString("\n\n"),
            nodes.copyableText(),
        )
    }

    /** A floor that is only a code block used to copy an empty string and still say it had copied. */
    @Test
    fun `a floor that is only code copies the code`() {
        assertEquals("rm -rf /tmp/x", listOf(RichNode.CodeBlock(code = "rm -rf /tmp/x", language = "sh")).copyableText())
    }

    @Test
    fun `a floor with nothing to read copies nothing`() {
        assertEquals("", listOf(RichNode.VotePlaceholder(voteId = 1)).copyableText())
        assertFalse(listOf(RichNode.VotePlaceholder(voteId = 1)).hasCopyableText())
    }

    /** A picture's address is not 正文, so a floor that is only pictures has no text to copy. */
    @Test
    fun `a floor that is only a picture has no text to copy`() {
        val nodes =
            listOf(
                RichNode.BlockImage(url = "https://i.example/a.png", alt = null),
                paragraph(InlineNode.Image(url = "https://i.example/b.png", alt = "截图")),
            )

        assertFalse(nodes.hasCopyableText())
    }

    /** Beside words a picture is still copied, as its address — a copy that lost it would read as whole. */
    @Test
    fun `a picture among words is copied as its address`() {
        val nodes =
            listOf(
                paragraph(text("晒单")),
                RichNode.BlockImage(url = "https://i.example/a.png", alt = null),
            )

        assertTrue(nodes.hasCopyableText())
        assertEquals("晒单\n\nhttps://i.example/a.png", nodes.copyableText())
    }
}
