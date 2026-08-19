package io.github.nodyssey.data.offline

import io.github.nodyssey.model.PostContent
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode

/**
 * Every picture one floor needs in order to render exactly as it does online.
 *
 * The walk goes into wrappers — quotes, folds, tab groups, list items, table cells — because that
 * is where posts on this site actually put their screenshots, and a downloaded thread whose images
 * only survived at the top level would be a copy that looks whole until the reader scrolls into the
 * 折叠 block. Avatars are included: they are shared between every floor by the same author and cost
 * one file each, and a thread of blank circles does not read as the thread that was downloaded.
 *
 * Stickers count too — they are the site's own emoji and a body written in them is empty without.
 */
internal fun PostContent.imageUrls(): Set<String> {
    val urls = LinkedHashSet<String>()
    avatarUrl?.let(urls::add)
    nodes.forEach { it.collectImageUrls(urls) }
    signatureNodes.forEach { it.collectImageUrls(urls) }
    return urls.filterTo(LinkedHashSet()) { it.startsWith("http://") || it.startsWith("https://") }
}

private fun RichNode.collectImageUrls(into: MutableSet<String>) {
    when (this) {
        is RichNode.BlockImage -> into += url

        is RichNode.Paragraph -> inlines.forEach { it.collectImageUrls(into) }

        is RichNode.Heading -> inlines.forEach { it.collectImageUrls(into) }

        is RichNode.Quote -> children.forEach { it.collectImageUrls(into) }

        is RichNode.Fold -> children.forEach { it.collectImageUrls(into) }

        is RichNode.Tabs -> tabs.forEach { tab -> tab.children.forEach { it.collectImageUrls(into) } }

        is RichNode.ListBlock -> items.forEach { item -> item.forEach { it.collectImageUrls(into) } }

        is RichNode.Table ->
            content.forEach { row -> row.forEach { cell -> cell.forEach { it.collectImageUrls(into) } } }

        // Code, polls, receive codes and rules carry no pictures of their own.
        else -> Unit
    }
}

private fun InlineNode.collectImageUrls(into: MutableSet<String>) {
    when (this) {
        is InlineNode.Image -> into += url
        is InlineNode.Sticker -> into += url
        else -> Unit
    }
}
