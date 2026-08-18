package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The review board's benchmark posts, which are the densest markup NodeSeek produces.
 *
 * Shape taken verbatim from post 845099: four sibling title/body pairs, two of them holding an
 * `language-ansi` report and two holding a screenshot.
 */
class MagicTabsParserTest {

    private fun parse(html: String) = RichContentParser.parse(Ksoup.parse("<article>$html</article>").selectFirst("article"))

    private val report =
        """
        <div class="nsk-magic-tabs"><div class="nsk-magic-tab-title">💻基本信息</div>
        <div class="nsk-magic-tab-body"><pre><code class="language-ansi">++++++++
<span data-ansicode="27"></span>[36m容器/虚拟化：<span data-ansicode="27"></span>[32mKVM 虚拟机<span data-ansicode="27"></span>[0m
</code></pre>
        </div><div class="nsk-magic-tab-title">🎬IP质量</div>
        <div class="nsk-magic-tab-body"><pre><code class="language-ansi">########
<span data-ansicode="27"></span>[36m自治系统号：<span data-ansicode="27"></span>[32mAS3257<span data-ansicode="27"></span>[0m
</code></pre>
        </div><div class="nsk-magic-tab-title">🌐网络质量</div>
        <div class="nsk-magic-tab-body"><p><img src="https://i.111666.best/image/n1Uzbj2KRJVCSvST2nR6Zc.webp" alt="image"></p>
        </div></div>
        """.trimIndent()

    @Test
    fun `keeps the tab group together instead of flattening it into one paragraph`() {
        val nodes = parse(report)

        val tabs = nodes.filterIsInstance<RichNode.Tabs>().single()
        assertEquals(listOf("💻基本信息", "🎬IP质量", "🌐网络质量"), tabs.tabs.map { it.title })
    }

    /**
     * The regression that made a report unreadable: with the wrapper unrecognised the reports became
     * `InlineNode.Text(code = true)` inside a single paragraph, so they rendered at body size and
     * wrapped. Nothing may survive at the top level except the group.
     */
    @Test
    fun `the reports are code blocks rather than inline code in a body paragraph`() {
        val nodes = parse(report)

        assertEquals(1, nodes.size)
        val bodies = nodes.filterIsInstance<RichNode.Tabs>().single().tabs.map { it.children }

        val hardware = bodies[0].single() as RichNode.CodeBlock
        assertEquals("ansi", hardware.language)
        assertTrue(hardware.code.startsWith("++++++++"))

        val ip = bodies[1].single() as RichNode.CodeBlock
        assertTrue(ip.code.contains("AS3257"))
    }

    @Test
    fun `strips the escape sequences that used to show up as literal bracket codes`() {
        val hardware =
            parse(report)
                .filterIsInstance<RichNode.Tabs>()
                .single()
                .tabs[0]
                .children
                .single() as RichNode.CodeBlock

        assertTrue(!hardware.code.contains("[36m"), "escape leaked: ${hardware.code}")
        assertTrue(hardware.code.contains("容器/虚拟化：KVM 虚拟机"))
        assertEquals(2, hardware.spans.size)
    }

    /** A screenshot tab has to stay inside its tab rather than floating up to the post body. */
    @Test
    fun `an image tab keeps its image`() {
        val networkTab =
            parse(report).filterIsInstance<RichNode.Tabs>().single().tabs[2]

        val image = networkTab.children.single() as RichNode.BlockImage
        assertEquals("https://i.111666.best/image/n1Uzbj2KRJVCSvST2nR6Zc.webp", image.url)
    }

    @Test
    fun `measures the report in terminal columns`() {
        val hardware =
            parse(report)
                .filterIsInstance<RichNode.Tabs>()
                .single()
                .tabs[0]
                .children
                .single() as RichNode.CodeBlock

        // "容器/虚拟化：KVM 虚拟机" — nine wide glyphs (the full-width colon counts) at two cells
        // each, plus five narrow ones. The 8-column rule line above it does not win.
        assertEquals(23, hardware.columns)
    }

    @Test
    fun `content before the first title is kept ahead of the group`() {
        val nodes =
            parse(
                """
                <div class="nsk-magic-tabs"><p>报告如下</p><div class="nsk-magic-tab-title">💻基本信息</div>
                <div class="nsk-magic-tab-body"><p>正文</p></div></div>
                """.trimIndent(),
            )

        assertEquals(2, nodes.size)
        assertTrue(nodes[0] is RichNode.Paragraph)
        assertTrue(nodes[1] is RichNode.Tabs)
    }

    /** If the site renames its classes, losing the post entirely is worse than losing the tabs. */
    @Test
    fun `a group with no recognisable titles falls back to plain blocks`() {
        val nodes = parse("""<div class="nsk-magic-tabs"><p>只有正文</p></div>""")

        assertTrue(nodes.none { it is RichNode.Tabs })
        assertEquals(1, nodes.filterIsInstance<RichNode.Paragraph>().size)
    }

    @Test
    fun `ordinary code blocks are unaffected`() {
        val nodes = parse("<pre><code class=\"language-bash\">curl -sL https://run.nodequality.com | bash</code></pre>")

        val code = nodes.single() as RichNode.CodeBlock
        assertEquals("bash", code.language)
        assertEquals("curl -sL https://run.nodequality.com | bash", code.code)
        assertTrue(code.spans.isEmpty())
    }
}
