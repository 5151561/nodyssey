package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.richtext.RichNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 折叠 — the `<details>` block the site's editor writes.
 *
 * Shape taken verbatim from post 876332, which is two folds with a `nsk-magic-tabs` group and a
 * link paragraph inside each. That post is what showed the bug: with `details` unrecognised the
 * whole thing came through as one run-on paragraph.
 */
class FoldParserTest {

    private fun parse(html: String) = RichContentParser.parse(Ksoup.parse("<article>$html</article>").selectFirst("article"))

    private val post =
        """
        <details><summary>TCP 调优前</summary>
        <div class="nsk-magic-tabs enabled"><div class="nsk-magic-tab-title is-active">IPv4回程</div>
        <div class="nsk-magic-tab-body"><p><img src="https://tcpquality.invalid/r/a.png?section=ipv4" alt="" class=""></p>
        </div><div class="nsk-magic-tab-title">单线程测速</div>
        <div class="nsk-magic-tab-body"><p><img src="https://tcpquality.invalid/r/a.png?section=speedtest" alt="" class=""></p>
        </div></div>
        <p>报告链接：<a href="/jump?to=https%3A%2F%2Ftcpquality.invalid%2Fr%2Fa" target="_blank">https://tcpquality.invalid/r/a</a></p>
        </details>
        <details><summary>TCP 调优后</summary>
        <p>后面的内容</p>
        </details>
        """.trimIndent()

    @Test
    fun `keeps each fold as its own block instead of flattening the article into one paragraph`() {
        val nodes = parse(post)

        val folds = nodes.filterIsInstance<RichNode.Fold>()
        assertEquals(listOf("TCP 调优前", "TCP 调优后"), folds.map { it.title })
        assertEquals(2, nodes.size)
    }

    /** The summary labels the block; left among the children it draws as a paragraph above it. */
    @Test
    fun `the summary is the title and not also a child`() {
        val fold = parse("<details><summary>标题</summary><p>正文</p></details>").single() as RichNode.Fold

        assertEquals("标题", fold.title)
        val body = fold.children.single() as RichNode.Paragraph
        assertEquals("正文", body.inlines.joinToString("") { (it as io.github.plaza.core.richtext.InlineNode.Text).text })
    }

    /**
     * The regression this node exists for: a fold's contents go through the ordinary block path, so
     * a tab group inside one survives as a group rather than having all its tabs poured out at once.
     */
    @Test
    fun `structure inside a fold survives`() {
        val fold = parse(post).first() as RichNode.Fold

        val tabs = fold.children.filterIsInstance<RichNode.Tabs>().single()
        assertEquals(listOf("IPv4回程", "单线程测速"), tabs.tabs.map { it.title })
        assertTrue(tabs.tabs.all { it.children.single() is RichNode.BlockImage })
        // The link paragraph is a sibling of the group, still inside the fold.
        assertTrue(fold.children.last() is RichNode.Paragraph)
    }

    /** `open` is the tag's own attribute: absent means the author folded it away, and it stays shut. */
    @Test
    fun `open follows the attribute`() {
        assertFalse((parse("<details><summary>a</summary><p>b</p></details>").single() as RichNode.Fold).open)
        assertTrue((parse("<details open><summary>a</summary><p>b</p></details>").single() as RichNode.Fold).open)
    }

    /** A `<summary>`-less block is still a fold; the renderer supplies the label. */
    @Test
    fun `a fold without a summary keeps its contents`() {
        val fold = parse("<details><p>正文</p></details>").single() as RichNode.Fold

        assertEquals("", fold.title)
        assertEquals(1, fold.children.size)
    }
}
