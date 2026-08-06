package io.github.nodyssey.core.html

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.model.InlineNode
import io.github.nodyssey.model.InlineStyle
import io.github.nodyssey.model.RichNode
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

    /**
     * The site's own tab extension, which the review board uses for benchmark reports.
     *
     * The titles and the bodies are siblings rather than nested — the site's stylesheet pairs each
     * title with the body that immediately follows it — so the grouping only exists if the parser
     * reconstructs it.
     */
    private const val MAGIC_TABS_CLASS = "nsk-magic-tabs"
    private const val TAB_TITLE_CLASS = "nsk-magic-tab-title"
    private const val TAB_BODY_CLASS = "nsk-magic-tab-body"

    fun parse(article: Element?): List<RichNode> {
        if (article == null) return emptyList()
        return parseBlocks(article.childNodes())
    }

    private fun parseBlocks(nodes: List<Node>): List<RichNode> {
        val blocks = mutableListOf<RichNode>()
        // Inline content that appears directly between block tags still needs a paragraph to live in.
        val looseInlines = mutableListOf<InlineNode>()

        fun flushLoose() {
            val trimmed = finishInlines(looseInlines)
            if (trimmed.isNotEmpty()) blocks += RichNode.Paragraph(trimmed)
            looseInlines.clear()
        }

        for (node in nodes) {
            when {
                node is TextNode -> {
                    if (node.text().isNotBlank()) looseInlines += InlineNode.Text(node.text())
                }

                node is Element -> {
                    val parsedBlocks = parseBlockElement(node)
                    if (parsedBlocks != null) {
                        flushLoose()
                        blocks += parsedBlocks
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
    private fun parseBlockElement(element: Element): List<RichNode>? = when (element.tagName()) {
        "p" -> paragraphBlocks(element)

        "h1", "h2", "h3", "h4", "h5", "h6" ->
            listOf(
                RichNode.Heading(
                    level = element.tagName().substring(1).toIntOrNull() ?: 3,
                    inlines = finishInlines(parseInlines(element.childNodes())),
                ),
            )

        "pre" -> listOf(codeBlock(element))

        "blockquote" -> listOf(RichNode.Quote(parseBlocks(element.childNodes())))

        "ul" -> listOf(listBlock(element, ordered = false))

        "ol" -> listOf(listBlock(element, ordered = true))

        "hr" -> listOf(RichNode.Divider)

        "table" -> listOf(table(element))

        "div" ->
            when {
                element.hasClass("quote") -> listOf(RichNode.Quote(parseBlocks(element.childNodes())))
                element.hasClass(MAGIC_TABS_CLASS) -> magicTabs(element)
                else -> paragraphBlocks(element)
            }

        "img" -> if (isSticker(element)) null else listOf(blockImage(element))

        // A vote marker that was not wrapped in a paragraph — the markdown renderer usually wraps it,
        // but a body that is nothing but the marker leaves it hanging directly off the article.
        "a" -> element.voteId()?.let { listOf(RichNode.VotePlaceholder(it)) }

        else -> null
    }

    /**
     * Ordinary images are blocks even when the site's generated HTML leaves them inside a text
     * paragraph. Only NodeSeek's own stickers participate in the inline text layout.
     *
     * Splitting here also preserves text on both sides of an image instead of forcing every image
     * through [InlineNode.Sticker]'s 20sp placeholder.
     */
    private fun paragraphBlocks(element: Element): List<RichNode> {
        val blocks = mutableListOf<RichNode>()
        val inlineNodes = mutableListOf<Node>()

        fun flushInlineNodes() {
            val inlines = finishInlines(parseInlines(inlineNodes))
            if (inlines.isNotEmpty()) blocks += RichNode.Paragraph(inlines)
            inlineNodes.clear()
        }

        element.childNodes().forEach { child ->
            // Checked before the image, because a vote marker is an anchor and `blockImageElement`
            // accepts `<a><img></a>` — an unlucky marker containing an image would be read as one.
            val voteId = child.voteId()
            val image = if (voteId == null) child.blockImageElement() else null
            when {
                voteId != null -> {
                    flushInlineNodes()
                    blocks += RichNode.VotePlaceholder(voteId)
                }

                image != null -> {
                    flushInlineNodes()
                    blocks += blockImage(image)
                }

                else -> inlineNodes += child
            }
        }
        flushInlineNodes()
        return blocks
    }

    /**
     * Matches the id in `nsapp://vote?id=2871`.
     *
     * A regex rather than URI parsing: `nsapp://` is not a scheme any URI parser handles usefully,
     * and the only field that has ever appeared is this one.
     */
    private val VOTE_ID = Regex("""\bid=(\d+)""")

    /** This node's vote id, or null when it is not a vote marker. */
    private fun Node.voteId(): Long? {
        val element = this as? Element ?: return null
        val anchor =
            if (element.tagName() == "a") element else element.selectFirst(Selectors.VOTE_PLACEHOLDER)
        val href = anchor?.attr(Selectors.VOTE_PLACEHOLDER_ATTR).orEmpty()
        if (!href.startsWith(Selectors.VOTE_PLACEHOLDER_SCHEME)) return null
        return VOTE_ID.find(href)?.groupValues?.get(1)?.toLongOrNull()
    }

    /** Accepts both a bare image and the common `<a><img></a>` image-link markup. */
    private fun Node.blockImageElement(): Element? {
        val element = this as? Element ?: return null
        if (element.tagName() == "img") return element.takeUnless(::isSticker)
        if (element.text().isNotBlank()) return null
        return element.select("img").singleOrNull()?.takeUnless(::isSticker)
    }

    private fun blockImage(element: Element): RichNode {
        val url = NodeSeekSite.absoluteUrl(element.attr("src")).orEmpty()
        return RichNode.BlockImage(url = url, alt = element.attr("alt").ifBlank { null })
    }

    /**
     * Splits a `nsk-magic-tabs` group back into titled tabs.
     *
     * Anything that is neither a title nor a body joins the tab being built — the site's own
     * stylesheet writes off such children as an authoring mistake, and dropping them here would
     * lose post content over one.
     */
    private fun magicTabs(element: Element): List<RichNode> {
        val tabs = mutableListOf<RichNode.Tabs.Tab>()
        // Content before the first title belongs to no tab, and is kept ahead of the group.
        val orphans = mutableListOf<Node>()
        var title: String? = null
        val body = mutableListOf<Node>()

        fun flush() {
            val pending = title ?: return
            tabs += RichNode.Tabs.Tab(title = pending, children = parseBlocks(body.toList()))
            title = null
            body.clear()
        }

        for (child in element.children()) {
            when {
                child.hasClass(TAB_TITLE_CLASS) -> {
                    flush()
                    title = child.text().trim()
                }

                // `add`/`addAll` rather than `+=`: an `Element` is itself iterable, so `+=` reads as
                // "append its children" to the compiler.
                child.hasClass(TAB_BODY_CLASS) -> body.addAll(child.childNodes())

                title == null -> orphans.add(child)

                else -> body.add(child)
            }
        }
        flush()

        // A group whose titles the site renamed is still a group of content; falling back to the
        // old flattening beats rendering an empty tab strip.
        if (tabs.isEmpty()) return paragraphBlocks(element)
        return parseBlocks(orphans) + RichNode.Tabs(tabs)
    }

    private fun codeBlock(element: Element): RichNode {
        val code = element.selectFirst("code")
        val language = code?.classNames()
            ?.firstOrNull { it.startsWith("language-") }
            ?.removePrefix("language-")
        // Read through [AnsiParser] rather than `wholeText()`: the escapes NodeSeek encodes as empty
        // elements are invisible to it, which leaves their parameters behind as literal `[36m`.
        val decoded = AnsiParser.decode(AnsiParser.sourceOf(code ?: element))
        return RichNode.CodeBlock(
            code = decoded.text,
            language = language,
            spans = decoded.spans,
            columns = decoded.columns,
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

                    /*
                     * A vote marker that reached the inline flow anyway — wrapped in `<strong>`, say,
                     * so neither block path caught it. Dropped rather than linked: its `href` is
                     * `javascript://void(0)` and its text is the raw `nsapp://vote?id=2871`, which is
                     * exactly the broken rendering this parser exists to stop.
                     */
                    "a" -> if (node.voteId() == null) result += link(node, style)

                    "img" ->
                        if (isSticker(node)) {
                            NodeSeekSite.absoluteUrl(node.attr("src"))?.let {
                                result += InlineNode.Sticker(it, node.attr("alt").ifBlank { null })
                            }
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

    /** Everything a finished run of inline content needs: trimmed, with quote references folded. */
    private fun finishInlines(inlines: List<InlineNode>): List<InlineNode> = foldQuoteRefs(trimInlines(inlines))

    private val FLOOR_LABEL = Regex("""^#\d+$""")

    /**
     * Folds NodeSeek's two-anchor quote reference (`<a>@name</a> <a>#3</a>`) into one node.
     *
     * Left alone it renders as two consecutive blue links, which is both ugly at the head of almost
     * every reply and wrong — tapping either one would leave for the web view rather than scrolling
     * to the floor being answered.
     */
    private fun foldQuoteRefs(inlines: List<InlineNode>): List<InlineNode> {
        if (inlines.size < 2) return inlines

        val result = mutableListOf<InlineNode>()
        var index = 0
        while (index < inlines.size) {
            val mention = inlines[index] as? InlineNode.Link
            if (mention != null && mention.text.startsWith("@")) {
                // The site puts a single space between the two anchors; anything else is not a pair.
                var next = index + 1
                if ((inlines.getOrNull(next) as? InlineNode.Text)?.text?.isBlank() == true) next++

                val floor = inlines.getOrNull(next) as? InlineNode.Link
                if (floor != null && FLOOR_LABEL.matches(floor.text.trim())) {
                    result +=
                        InlineNode.QuoteRef(
                            name = mention.text.removePrefix("@"),
                            floor = floor.text.trim(),
                            url = floor.url,
                        )
                    index = next + 1
                    continue
                }
            }
            result += inlines[index]
            index++
        }
        return result
    }

    /** Drops leading/trailing whitespace-only text so paragraphs do not start with a blank line. */
    private fun trimInlines(inlines: List<InlineNode>): List<InlineNode> {
        var start = 0
        var end = inlines.size
        while (start < end && isBlank(inlines[start])) start++
        while (end > start && isBlank(inlines[end - 1])) end--
        return inlines.subList(start, end).toList()
    }

    private fun isBlank(node: InlineNode): Boolean =
        (node is InlineNode.Text && node.text.isBlank()) || node is InlineNode.LineBreak
}
