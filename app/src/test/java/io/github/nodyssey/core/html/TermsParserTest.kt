package io.github.nodyssey.core.html

import io.github.nodyssey.model.TermsBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class TermsParserTest {
    @Test
    fun `parses title date headings paragraphs and lists`() {
        val document = TermsParser.parse(
            """
            <html><body><article>
              <h2>本网站服务协议</h2>
              <p>最新版本生效日期：2022-11-24</p>
              <h2>定义和说明</h2>
              <p>协议正文。</p>
              <ul><li>第一项</li><li>第二项</li></ul>
              <h3>注册</h3>
              <ol><li>注册条款</li></ol>
            </article></body></html>
            """.trimIndent(),
        )

        assertEquals("本网站服务协议", document.title)
        assertEquals("2022-11-24", document.effectiveDate)
        assertEquals(
            listOf(
                TermsBlock.Heading(2, "定义和说明"),
                TermsBlock.Paragraph("协议正文。"),
                TermsBlock.ListBlock(false, listOf("第一项", "第二项")),
                TermsBlock.Heading(3, "注册"),
                TermsBlock.ListBlock(true, listOf("注册条款")),
            ),
            document.blocks,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects pages without an article`() {
        TermsParser.parse("<html><body>Cloudflare challenge</body></html>")
    }
}
