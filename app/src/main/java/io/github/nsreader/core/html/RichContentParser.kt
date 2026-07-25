package io.github.nsreader.core.html

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.model.InlineNode
import io.github.nsreader.model.InlineStyle
import io.github.nsreader.model.RichNode
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Turns the HTML inside `article.post-content` into a block/inline tree the Compose renderer can
 * draw natively. NodeSeek renders Markdown server-side, so the tag vocabulary is small and stable:
 * paragraphs, images, links, code, quotes, lists and tables.
 */
object RichContentParser {

    /** NodeSeek's built-in emoji are `<img class="sticker">` and must stay inline with the text. */
    private const val STICKER_CLASS = "sticker"

    fun parse(article: Element?): List<RichNode> {
        if (article == null) return emptyList()
        return parseBlocks(article.childNodes())
    }

    private fun parseBlocks(nodes: List<Node>): List<RichNode> {
        val blocks = mutableListOf<RichNode>()
        // Inline content that appears directly between block tags still needs a paragraph to live in.
        val looseInlines = mutableListOf<InlineNode>()

        fun flushLoose() {
            val trimmed = trimInlines(looseInlines)
            if (trimmed.isNotEmpty()) blocks += RichNode.Paragraph(trimmed)
            looseInlines.clear()
        }

        for (node in nodes) {
            when {
                node is TextNode -> {
                    if (node.text().isNotBlank()) looseInlines += InlineNode.Text(node.text())
                }

                node is Element -> {
                    val block = parseBlockElement(node)
                    if (block != null) {
                        flushLoose()
                        blocks += block
                    } else {
                        looseInlines += parseInlines(listOf(node))
                    }
                }
            }
        }
        flushLoose()
        return blocks
    }

    /** Returns null when the element is inline and should be folded into the surrounding paragraph. */
    private fun parseBlockElement(element: Element): RichNode? = when (element.tagName()) {
        "p" -> paragraphOrImage(element)
        "h1", "h2", "h3", "h4", "h5", "h6" ->
            RichNode.Heading(
                level = element.tagName().substring(1).toIntOrNull() ?: 3,
                inlines = trimInlines(parseInlines(element.childNodes())),
            )

        "pre" -> codeBlock(element)
        "blockquote" -> RichNode.Quote(parseBlocks(element.childNodes()))
        "ul" -> listBlock(element, ordered = false)
        "ol" -> listBlock(element, ordered = true)
        "hr" -> RichNode.Divider
        "table" -> table(element)
        "div" ->
            if (element.hasClass("quote")) {
                RichNode.Quote(parseBlocks(element.childNodes()))
            } else {
                RichNode.Paragraph(trimInlines(parseInlines(element.childNodes())))
            }

        "img" -> if (isSticker(element)) null else blockImage(element)
        else -> null
    }

    /**
     * A paragraph holding nothing but images is really a figure — rendering those full width
     * instead of inline is the single biggest readability win over the mobile web layout.
     */
    private fun paragraphOrImage(element: Element): RichNode {
        val images = element.select("img").filterNot { isSticker(it) }
        val hasText = element.text().isNotBlank()
        if (!hasText && images.size == 1) return blockImage(images.first())
        if (!hasText && images.size > 1) {
            // Multiple bare images: keep the first as the block and let the rest follow as inlines.
            return RichNode.Paragraph(images.mapNotNull { img ->
                NodeSeekSite.absoluteUrl(img.attr("src"))?.let { InlineNode.Sticker(it, img.attr("alt")) }
            })
        }
        return RichNode.Paragraph(trimInlines(parseInlines(element.childNodes())))
    }

    private fun blockImage(element: Element): RichNode {
        val url = NodeSeekSite.absoluteUrl(element.attr("src")).orEmpty()
        return RichNode.BlockImage(url = url, alt = element.attr("alt").ifBlank { null })
    }

    private fun codeBlock(element: Element): RichNode {
        val code = element.selectFirst("code")
        val language = code?.classNames()
            ?.firstOrNull { it.startsWith("language-") }
            ?.removePrefix("language-")
        // `wholeText` keeps the original newlines that `text()` would collapse.
        return RichNode.CodeBlock(
            code = (code ?: element).wholeText().trimEnd(),
            language = language,
        )
    }

    private fun listBlock(element: Element, ordered: Boolean): RichNode {
        val items = element.children()
            .filter { it.tagName() == "li" }
            .map { parseBlocks(it.childNodes()) }
        return RichNode.ListBlock(ordered = ordered, items = items)
    }

    private fun table(element: Element): RichNode {
        val rows = element.select("tr").map { row ->
            row.select("th, td").map { it.text() }
        }.filter { it.isNotEmpty() }
        return RichNode.Table(rows)
    }

    // --- Inline -------------------------------------------------------------

    private fun parseInlines(
        nodes: List<Node>,
        style: InlineStyle = InlineStyle(),
    ): List<InlineNode> {
        val result = mutableListOf<InlineNode>()
        for (node in nodes) {
            when (node) {
                is TextNode -> if (node.text().isNotEmpty()) result += InlineNode.Text(node.text(), style)

                is Element -> when (node.tagName()) {
                    "br" -> result += InlineNode.LineBreak
                    "strong", "b" -> result += parseInlines(node.childNodes(), style.copy(bold = true))
                    "em", "i" -> result += parseInlines(node.childNodes(), style.copy(italic = true))
                    "del", "s", "strike" ->
                        result += parseInlines(node.childNodes(), style.copy(strikethrough = true))

                    "code" -> result += InlineNode.Text(node.wholeText(), style.copy(code = true))
                    "a" -> result += link(node, style)
                    "img" -> NodeSeekSite.absoluteUrl(node.attr("src"))?.let {
                        result += InlineNode.Sticker(it, node.attr("alt").ifBlank { null })
                    }

                    else -> result += parseInlines(node.childNodes(), style)
                }
            }
        }
        return result
    }

    private fun link(element: Element, style: InlineStyle): List<InlineNode> {
        val url = NodeSeekSite.absoluteUrl(element.attr("href"))
        val text = element.text()
        // An anchor wrapping only an image should still render as the image.
        if (text.isBlank()) return parseInlines(element.childNodes(), style)
        return if (url == null) {
            listOf(InlineNode.Text(text, style))
        } else {
            listOf(InlineNode.Link(text = text, url = url, style = style))
        }
    }

    private fun isSticker(element: Element): Boolean =
        element.hasClass(STICKER_CLASS) || element.attr("src").contains("/static/image/sticker/")

    /** Drops leading/trailing whitespace-only text so paragraphs do not start with a blank line. */
    private fun trimInlines(inlines: List<InlineNode>): List<InlineNode> {
        var start = 0
        var end = inlines.size
        while (start < end && isBlank(inlines[start])) start++
        while (end > start && isBlank(inlines[end - 1])) end--
        return inlines.subList(start, end).toList()
    }

    private fun isBlank(node: InlineNode): Boolean =
        node is InlineNode.Text && node.text.isBlank() || node is InlineNode.LineBreak
}
