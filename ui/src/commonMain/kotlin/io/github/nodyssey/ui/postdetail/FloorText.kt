package io.github.nodyssey.ui.postdetail

import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode

/**
 * A floor as the plain text 复制正文 puts on the clipboard: every block that carries words, in order.
 *
 * Not the excerpt the panel's head and a quote use, which keeps paragraphs and headings only — a
 * copy made from that dropped the code block out of a benchmark post, the steps out of a tutorial,
 * and turned a floor that was all code into an empty string that still said it had been copied.
 *
 * Blocks are separated by a blank line, a hard line break stays a line break, a list keeps its
 * markers, a quote its `>` and a table its rows with the cells tab-separated, which is what a
 * spreadsheet or an editor makes of a paste. Pictures among the words are kept as their addresses:
 * a copy that silently lost them would read as complete. A poll and a stardust transfer have no text
 * of their own and are left out.
 *
 * Whether there is anything to copy at all is [hasCopyableText], which a picture does not answer.
 */
internal fun List<RichNode>.copyableText(): String = blocksText(this, images = true).trim()

/**
 * Whether the floor has words of its own — what decides that 复制正文 is offered at all.
 *
 * A picture's address is not 正文: a floor that is only a screenshot would put a URL on the clipboard
 * and say the text had been copied. Pictures still go along with [copyableText] when there are words.
 */
internal fun List<RichNode>.hasCopyableText(): Boolean = blocksText(this, images = false).isNotBlank()

private fun blocksText(
    nodes: List<RichNode>,
    images: Boolean,
): String = nodes.mapNotNull { blockText(it, images)?.takeIf(String::isNotBlank) }.joinToString("\n\n")

private fun blockText(
    node: RichNode,
    images: Boolean,
): String? =
    when (node) {
        is RichNode.Paragraph -> node.inlines.inlineText(images).trim()

        is RichNode.Heading -> node.inlines.inlineText(images).trim()

        is RichNode.CodeBlock -> node.code.trimEnd('\n')

        is RichNode.Quote -> blocksText(node.children, images).lines().joinToString("\n") { "> $it".trimEnd() }

        is RichNode.ListBlock ->
            node.items.mapIndexed { index, item ->
                val marker = if (node.ordered) "${index + 1}. " else "- "
                val indent = " ".repeat(marker.length)
                blocksText(item, images)
                    .lines()
                    .mapIndexed { line, text -> (if (line == 0) marker + text else indent + text).trimEnd() }
                    .joinToString("\n")
            }.joinToString("\n")

        is RichNode.Table ->
            node.content.joinToString("\n") { row -> row.joinToString("\t") { cell -> cell.inlineText(images).trim() } }

        is RichNode.Fold -> listOf(node.title, blocksText(node.children, images)).filter(String::isNotBlank).joinToString("\n")

        is RichNode.Tabs ->
            node.tabs.joinToString("\n\n") { tab ->
                listOf(tab.title, blocksText(tab.children, images)).filter(String::isNotBlank).joinToString("\n")
            }

        is RichNode.BlockImage -> node.url.takeIf { images }

        is RichNode.VotePlaceholder, is RichNode.StardustReceive, RichNode.Divider -> null
    }

private fun List<InlineNode>.inlineText(images: Boolean): String =
    joinToString("") { inline ->
        when (inline) {
            is InlineNode.Text -> inline.text
            is InlineNode.Link -> inline.text
            is InlineNode.Sticker -> inline.alt.orEmpty()
            is InlineNode.Image -> if (images) inline.url else ""
            is InlineNode.QuoteRef -> "@${inline.name} ${inline.floor}"
            InlineNode.LineBreak -> "\n"
        }
    }
