package io.github.nodyssey.ui.postdetail

import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import org.junit.Assert.assertEquals
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
    }
}
