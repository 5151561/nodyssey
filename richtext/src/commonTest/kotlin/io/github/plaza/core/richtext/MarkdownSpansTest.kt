package io.github.plaza.core.richtext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownSpansTest {
    @Test
    fun `marks a heading's hashes apart from its text`() {
        val source = "## 标题"
        val spans = markdownSpans(source)

        assertEquals("## ", source.text(spans.only(MarkdownSpanKind.Marker)))
        assertEquals("标题", source.text(spans.only(MarkdownSpanKind.Heading)))
    }

    @Test
    fun `marks the delimiters of an emphasis run separately from what it emphasises`() {
        val source = "正文有 **加粗** 收尾"
        val spans = markdownSpans(source)

        assertEquals("加粗", source.text(spans.only(MarkdownSpanKind.Bold)))
        assertEquals(listOf("**", "**"), spans.texts(source, MarkdownSpanKind.Marker))
    }

    /**
     * `***x***` is bold *and* italic, both over the same characters, and the renderer resolves that
     * to the heavier weight. A renderer applies spans in the order they arrive, so the order is the
     * contract: italic first, bold last, or the editor would draw its strongest emphasis lighter
     * than its plain bold.
     */
    @Test
    fun `puts bold after italic when a run is both`() {
        val source = "***两者***"
        val kinds =
            markdownSpans(source)
                .map { it.kind }
                .filter { it == MarkdownSpanKind.Bold || it == MarkdownSpanKind.Italic }

        assertEquals(listOf(MarkdownSpanKind.Italic, MarkdownSpanKind.Bold), kinds)
    }

    @Test
    fun `marks a link's label apart from its destination`() {
        val source = "见 [名字](https://example.com \"标题\") 处"
        val spans = markdownSpans(source)

        assertEquals("名字", source.text(spans.only(MarkdownSpanKind.LinkText)))
        assertEquals("https://example.com \"标题\"", source.text(spans.only(MarkdownSpanKind.LinkUrl)))
    }

    /**
     * Everything between the fences is code, whatever it would have been on its own. A `#` inside a
     * shell transcript is a prompt, and a heading there is the one mistake this block exists to stop.
     */
    @Test
    fun `keeps a fenced block whole and reads no syntax inside it`() {
        val source =
            """
            ```bash
            # 注释
            echo ok
            ```
            """.trimIndent()
        val spans = markdownSpans(source)

        assertEquals("# 注释\necho ok", source.text(spans.only(MarkdownSpanKind.Code)))
        assertEquals(listOf("```bash", "```"), spans.texts(source, MarkdownSpanKind.Marker))
        assertTrue(spans.none { it.kind == MarkdownSpanKind.Heading })
    }

    /** What the author is looking at while they are still typing the block. */
    @Test
    fun `runs an unclosed fence to the end of the source`() {
        val source = "```\necho ok"

        assertEquals("echo ok", source.text(markdownSpans(source).only(MarkdownSpanKind.Code)))
    }

    @Test
    fun `strips one quote marker per level and marks what is left`() {
        val source = ">> 引用"
        val spans = markdownSpans(source)

        assertEquals(">> ", source.text(spans.only(MarkdownSpanKind.Marker)))
        assertEquals("引用", source.text(spans.only(MarkdownSpanKind.Quote)))
    }

    /**
     * A quote holds blocks, not a line of text: `> - **x**` is a list inside it, and the bullet is
     * syntax there for the same reason it is syntax at the start of a line.
     */
    @Test
    fun `reads block syntax inside a quoted line`() {
        val source = "> - **项**"
        val spans = markdownSpans(source)

        assertEquals("项", source.text(spans.only(MarkdownSpanKind.Bold)))
        assertEquals(listOf("> ", "- ", "**", "**"), spans.texts(source, MarkdownSpanKind.Marker))
    }

    /**
     * The rules that decide what is emphasis are the parser's, not a second copy of them — so the
     * cases it deliberately leaves alone have to come out unmarked here too. `2 * 3 * 4` is the one
     * that matters: read as emphasis it would italicise a sum.
     */
    @Test
    fun `leaves alone what the parser does not read as syntax`() {
        assertTrue(markdownSpans("2 * 3 * 4").none { it.kind == MarkdownSpanKind.Italic })
        assertTrue(markdownSpans("aurora_scbot_1 在线").none { it.kind == MarkdownSpanKind.Italic })
        assertTrue(markdownSpans("**未闭合").none { it.kind == MarkdownSpanKind.Bold })
        assertTrue(markdownSpans("[空]()").none { it.kind == MarkdownSpanKind.LinkText })
    }

    /**
     * The test that keeps the editor and the preview from drifting apart: whatever the parser ends up
     * rendering bold is exactly what gets marked bold, in the same order. The two walks are separate
     * code and will be edited separately; the dialect they walk must not be.
     */
    @Test
    fun `marks the same runs bold as the preview renders bold`() {
        val source =
            """
            ## **标题里的加粗**

            正文有 **加粗**、*斜体*、***两者***，还有 [**链接**](https://example.com)。

            - 列表里的 **一项**
            > 引用里的 **一句**
            """.trimIndent()

        val marked =
            markdownSpans(source)
                .filter { it.kind == MarkdownSpanKind.Bold }
                .map { source.substring(it.start, it.end) }
        val rendered =
            parseMarkdown(source)
                .inlines()
                .mapNotNull {
                    when {
                        it is InlineNode.Text && it.style.bold -> it.text
                        it is InlineNode.Link && it.style.bold -> it.text
                        else -> null
                    }
                }

        assertEquals(rendered, marked)
    }

    /** The same agreement for code, which the parser trims and this does not. */
    @Test
    fun `marks the same runs as code as the preview renders as code`() {
        val source = "跑 `./gradlew jvmTest` 或者 ``a ` b`` 看看"

        val marked =
            markdownSpans(source)
                .filter { it.kind == MarkdownSpanKind.Code }
                .map { source.substring(it.start, it.end).trim() }
        val rendered =
            parseMarkdown(source).inlines().filterIsInstance<InlineNode.Text>()
                .filter { it.style.code }
                .map { it.text }

        assertEquals(rendered, marked)
    }

    /**
     * `addStyle` throws on a range outside the text, so a span that is off by one is a crash in an
     * editor rather than a wrong colour. Everything the scan emits has to be inside the source and
     * hold at least one character.
     */
    @Test
    fun `emits only ranges that point into the source`() {
        val source =
            """
            # 标题

            ![图](https://example.com/a.png) 和 [链接](https://example.com)

            ```kotlin
            val x = 1
            ```

            1. 第一项
            - 第二项 ~~划掉~~ `code` \*转义\*
            > 引用
            ---
            | a | b |
            |---|---|
            | 1 | 2 |
            """.trimIndent()

        markdownSpans(source).forEach { span ->
            assertTrue(span.start in 0..source.length, "start ${span.start} of $span")
            assertTrue(span.end in 0..source.length, "end ${span.end} of $span")
            assertTrue(span.end > span.start, "empty $span")
        }
    }

    /**
     * Past the cap nothing is marked at all. A pasted log is the case: it is not being written, and
     * scanning it on every keystroke costs the typing that is.
     */
    @Test
    fun `gives up on a body too long to scan on every keystroke`() {
        assertTrue(markdownSpans("**加粗**").isNotEmpty())
        assertTrue(markdownSpans("**加粗**" + "文".repeat(20_000)).isEmpty())
    }
}

/** The one span of [kind], which every case above expects there to be exactly one of. */
private fun List<MarkdownSpan>.only(kind: MarkdownSpanKind): MarkdownSpan = single { it.kind == kind }

private fun String.text(span: MarkdownSpan): String = substring(span.start, span.end)

private fun List<MarkdownSpan>.texts(
    source: String,
    kind: MarkdownSpanKind,
): List<String> = filter { it.kind == kind }.map { source.substring(it.start, it.end) }

/** Every inline in a parsed body, wherever the block tree put it. */
private fun List<RichNode>.inlines(): List<InlineNode> =
    flatMap { node ->
        when (node) {
            is RichNode.Paragraph -> node.inlines
            is RichNode.Heading -> node.inlines
            is RichNode.Quote -> node.children.inlines()
            is RichNode.ListBlock -> node.items.flatMap { it.inlines() }
            is RichNode.Tabs -> node.tabs.flatMap { it.children.inlines() }
            is RichNode.Fold -> node.children.inlines()
            is RichNode.Table -> node.content.flatten().flatten()
            else -> emptyList()
        }
    }
