package io.github.nodyssey.core.html

import io.github.nodyssey.model.TermsBlock
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The payloads are `data-cfemail` values built the way Cloudflare builds them — first byte the key,
 * every byte after it one UTF-8 byte of the address XOR'd with it — so the expectations are the
 * addresses the site's own `email-decode.min.js` would put back.
 */
class SiteHtmlTest {

    @Test
    fun `puts the address back where Cloudflare left a placeholder`() {
        val paragraph = paragraphOf(
            """有事请发 <span class="__cf_email__" data-cfemail="2a424f4646456a4f524b475a464f04494547">[email&#160;protected]</span> 找我""",
        )
        assertEquals("有事请发 hello@example.com 找我", paragraph)
    }

    /** The payload is UTF-8 bytes, not chars: reading it per-char turns an IDN address into mojibake. */
    @Test
    fun `decodes an address outside ASCII`() {
        val paragraph = paragraphOf(
            """<span class="__cf_email__" data-cfemail="7b9ec7db9fc3f23b9fc5f09ed6eb559fc3d69ee0c6">[email&#160;protected]</span>""",
        )
        assertEquals("张三@例子.中国", paragraph)
    }

    /** A payload we cannot read leaves the element alone rather than writing a garbled string over it. */
    @Test
    fun `leaves a malformed payload untouched`() {
        val paragraph = paragraphOf("""<span class="__cf_email__" data-cfemail="zzzz">[email&#160;protected]</span>""")
        assertEquals("[email protected]", paragraph)
    }

    /**
     * The other shape Cloudflare produces: an address the author wrote as a link keeps their words
     * and gets its destination back, rather than pointing at Cloudflare's own explainer page.
     */
    @Test
    fun `an obfuscated mailto link points at the address again`() {
        val article = SiteHtml
            .parse(
                """<html><body><article><p>
                    <a href="/cdn-cgi/l/email-protection#2a424f4646456a4f524b475a464f04494547">联系我</a>
                </p></article></body></html>""",
            ).selectFirst("article")

        val link = RichContentParser
            .parse(article)
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap(RichNode.Paragraph::inlines)
            .filterIsInstance<InlineNode.Link>()
            .single()
        assertEquals("联系我", link.text)
        assertEquals("mailto:hello@example.com", link.url)
    }

    /** Runs the repair through a real page parser, so a parser that stops using [SiteHtml] fails here. */
    private fun paragraphOf(html: String): String {
        val terms = TermsParser.parse("<html><body><article><h1>条款</h1><p>$html</p></article></body></html>")
        return (terms.blocks.single() as TermsBlock.Paragraph).text
    }
}
