package io.github.nodyssey.data

import com.fleeksoft.ksoup.Ksoup
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.RichContentParser
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.core.richtext.parseMarkdown

/**
 * What a body that is not words becomes when it is flattened to one line.
 *
 * The label is the caller's: these end up on screen in the reader's own language, and this module
 * has no string catalogue. What belongs here is the *classification* — telling one of the site's own
 * stickers from a photograph is a fact about NodeSeek, not about the screen showing it.
 */
enum class PreviewPlaceholder { IMAGE, STICKER, CODE, TABLE, VOTE, PAYMENT }

/** One run of a flattened body: the words as written, or a stand-in for something that is not words. */
sealed interface PreviewPart {
    data class Text(val text: String) : PreviewPart

    data class Placeholder(val kind: PreviewPlaceholder) : PreviewPart
}

/**
 * A message reduced to the one line a list row can show — the 私信 list's snippet.
 *
 * Two things happen on the way, and each of them is what makes the line worth showing at all:
 *
 * - **The quotation goes.** A message written with the bubble's 引用 action opens with a blockquote
 *   of what it is answering, which is the message above it in the same conversation. Leading quotes
 *   are dropped, unless dropping them leaves nothing, in which case the quote was the whole message.
 * - **Pictures are named rather than drawn.** A row that is one sticker tall would push every other
 *   row down the screen, so an image becomes `[图片]` and a sticker `[表情]` — the reader can see
 *   that something was sent without the list turning into a gallery.
 *
 * [raw] is taken as Markdown unless it looks like markup, because the site's JSON is inconsistent
 * about which it sends: 私信 carry Markdown, while a rendered body arrives as HTML. Both parsers
 * produce the same tree, so the only difference is which one runs.
 */
fun contentPreview(
    raw: String?,
    limit: Int = PREVIEW_LIMIT,
): List<PreviewPart> {
    val source = raw?.trim().orEmpty()
    if (source.isEmpty()) return emptyList()
    val nodes = if (source.looksLikeMarkup()) parseMarkup(source) else parseMarkdown(source)
    val builder = PreviewBuilder(limit)
    builder.appendBlocks(nodes.withoutLeadingQuotes())
    return builder.build()
}

/** How many characters of text a preview keeps; the row ellipsizes whatever still does not fit. */
const val PREVIEW_LIMIT = 140

/** What a placeholder costs against [PREVIEW_LIMIT], so a wall of stickers cannot outrun it. */
private const val PLACEHOLDER_COST = 4

private fun parseMarkup(html: String): List<RichNode> = RichContentParser.parse(Ksoup.parse(html).body())

/**
 * Markup, not Markdown.
 *
 * A closing tag is the giveaway: Markdown has no `</…>`, and the site's rendered comments are
 * wrapped in at least one element. A stray `<` in prose — `a < b` — matches neither.
 */
private fun String.looksLikeMarkup(): Boolean = MARKUP.containsMatchIn(this)

private val MARKUP = Regex("</[a-zA-Z]|<(img|br|hr)\\b", RegexOption.IGNORE_CASE)

private fun List<RichNode>.withoutLeadingQuotes(): List<RichNode> {
    val rest = dropWhile { it is RichNode.Quote }
    return rest.ifEmpty { this }
}

/**
 * Collects the flattened runs, and stops collecting once [limit] characters have gone by.
 *
 * Adjacent text is merged as it arrives rather than joined at the end, because the blocks it comes
 * from are separated by a space here and by a line break in the original: `a\n\nb` has to read as
 * `a b`, and two `Text` parts in a row would be indistinguishable from a placeholder between them.
 */
private class PreviewBuilder(
    private val limit: Int,
) {
    private val parts = mutableListOf<PreviewPart>()
    private var used = 0
    private var full = false

    fun appendBlocks(nodes: List<RichNode>) {
        nodes.forEach { node ->
            if (full) return
            when (node) {
                is RichNode.Paragraph -> appendInlines(node.inlines)

                is RichNode.Heading -> appendInlines(node.inlines)

                is RichNode.BlockImage -> placeholder(node.url.pictureKind())

                is RichNode.CodeBlock -> placeholder(PreviewPlaceholder.CODE)

                is RichNode.Table -> placeholder(PreviewPlaceholder.TABLE)

                is RichNode.VotePlaceholder -> placeholder(PreviewPlaceholder.VOTE)

                is RichNode.StardustReceive -> placeholder(PreviewPlaceholder.PAYMENT)

                is RichNode.Quote -> appendBlocks(node.children)

                is RichNode.ListBlock -> node.items.forEach(::appendBlocks)

                is RichNode.Tabs -> node.tabs.forEach { appendBlocks(it.children) }

                is RichNode.Fold -> {
                    text(node.title)
                    appendBlocks(node.children)
                }

                RichNode.Divider -> Unit
            }
            // Blocks were paragraphs; without this the last word of one runs into the first of the next.
            text(" ")
        }
    }

    private fun appendInlines(inlines: List<InlineNode>) {
        inlines.forEach { inline ->
            if (full) return
            when (inline) {
                is InlineNode.Text -> text(inline.text)
                is InlineNode.Link -> text(inline.text)
                is InlineNode.Sticker -> placeholder(PreviewPlaceholder.STICKER)
                is InlineNode.Image -> placeholder(inline.url.pictureKind())
                is InlineNode.QuoteRef -> text("@${inline.name} ${inline.floor}")
                InlineNode.LineBreak -> text(" ")
            }
        }
    }

    private fun text(value: String) {
        if (full) return
        // Every kind of whitespace the body had becomes one space: the row is one line, and a
        // newline inside it would render as a gap the reader cannot account for.
        val collapsed = value.replace(WHITESPACE, " ")
        if (collapsed.isEmpty()) return
        val last = parts.lastOrNull()
        // Nothing yet, or a space against a space: neither is worth a run of its own.
        if (collapsed.isBlank() && (last == null || last.endsWithSpace())) return
        val room = limit - used
        val kept = if (collapsed.length <= room) collapsed else collapsed.take(room.coerceAtLeast(0)) + ELLIPSIS
        used += collapsed.length
        if (used >= limit) full = true
        if (last is PreviewPart.Text) {
            // One space at a join, never two: a block that follows a placeholder contributes the
            // separator above *and* whatever space its own text starts with, and `[图片]  看这个`
            // reads as a missing word rather than as a gap between two runs.
            parts[parts.lastIndex] =
                PreviewPart.Text(last.text + if (last.text.endsWith(' ')) kept.trimStart(' ') else kept)
        } else {
            parts += PreviewPart.Text(kept)
        }
    }

    private fun placeholder(kind: PreviewPlaceholder) {
        if (full) return
        parts += PreviewPart.Placeholder(kind)
        used += PLACEHOLDER_COST
        if (used >= limit) full = true
    }

    fun build(): List<PreviewPart> {
        val trimmed =
            parts.mapIndexed { index, part ->
                when {
                    part !is PreviewPart.Text -> part
                    index == 0 && index == parts.lastIndex -> PreviewPart.Text(part.text.trim())
                    index == 0 -> PreviewPart.Text(part.text.trimStart())
                    index == parts.lastIndex -> PreviewPart.Text(part.text.trimEnd())
                    else -> part
                }
            }
        return trimmed.filterNot { it is PreviewPart.Text && it.text.isEmpty() }
    }

    private fun PreviewPart.endsWithSpace(): Boolean = this is PreviewPart.Text && text.endsWith(' ')
}

private fun String.pictureKind(): PreviewPlaceholder =
    if (NodeSeekSite.isStickerUrl(this)) PreviewPlaceholder.STICKER else PreviewPlaceholder.IMAGE

private val WHITESPACE = Regex("\\s+")
private const val ELLIPSIS = "…"
