package io.github.plaza.core.richtext

import io.github.plaza.core.ansi.AnsiSpan
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The block/inline tree a forum body is rendered from.
 *
 * Sealed, and therefore whole: Kotlin will not let a consumer add a variant from another module, so
 * everything a body can contain has to be named here. That is the deliberate trade — a renderer's
 * `when` stays exhaustive, and what stays site-specific is the *parser* that decides which markup
 * becomes which node, plus the slots the renderer offers for the two nodes it cannot draw alone.
 *
 * Every type below carries an explicit, short [SerialName]. These end up inside cached rows that
 * outlive an install, so they must not be a Kotlin class name that a refactor could silently change
 * and make old rows unreadable. Moving this file between packages is safe for exactly that reason;
 * renaming a discriminator is not.
 */

/** Block-level pieces of rendered post markup. */
@Serializable
sealed interface RichNode {
    @Serializable
    @SerialName("p")
    data class Paragraph(
        val inlines: List<InlineNode>,
    ) : RichNode

    @Serializable
    @SerialName("h")
    data class Heading(
        val level: Int,
        val inlines: List<InlineNode>,
    ) : RichNode

    /** An image on its own line, rendered full width rather than inline with text. */
    @Serializable
    @SerialName("img")
    data class BlockImage(
        val url: String,
        val alt: String?,
    ) : RichNode

    /**
     * Where a poll goes. The body carries only its id.
     *
     * Nothing about the poll itself is stored — the options, the tallies and whether this account has
     * voted all take a separate request, and all of them change — so caching anything beyond the id
     * would be storing a stale answer as though it were the article. How a forum *writes* a poll into
     * a body is its own affair and belongs to whichever parser recognises it.
     *
     * Block-level, because a card with buttons cannot live inside an `AnnotatedString`. Like
     * [BlockImage] it splits the paragraph it was found in.
     *
     * Note for the next migration: this discriminator did not exist before v1.1.4, so a *downgrade*
     * would fail to decode bodies cached by a newer build. Reading old rows is unaffected.
     */
    @Serializable
    @SerialName("vote")
    data class VotePlaceholder(
        val voteId: Long,
    ) : RichNode

    @Serializable
    @SerialName("code")
    data class CodeBlock(
        val code: String,
        val language: String?,
        /**
         * Colour runs recovered from `language-ansi` output, empty for ordinary code.
         *
         * Index ranges into [code] rather than a pre-built annotated string, because the palette
         * these indices resolve against belongs to the renderer, not to the cache.
         */
        val spans: List<AnsiSpan> = emptyList(),
        /**
         * Width of the longest line in terminal columns, counting CJK and emoji as two.
         *
         * A benchmark report is 80 columns of aligned ASCII art that cannot be reflowed, so this is
         * what decides whether the block can be shown in the thread at all or has to open elsewhere.
         */
        val columns: Int = 0,
    ) : RichNode

    @Serializable
    @SerialName("quote")
    data class Quote(
        val children: List<RichNode>,
    ) : RichNode

    /**
     * A tab group, from whatever Markdown extension the site writes one with.
     *
     * Flattening it — which is what happens when the parser does not recognise the wrapper — puts
     * every tab's contents on screen at once, so the group has to survive parsing as a group.
     */
    @Serializable
    @SerialName("tabs")
    data class Tabs(
        val tabs: List<Tab>,
    ) : RichNode {
        @Serializable
        data class Tab(
            val title: String,
            val children: List<RichNode>,
        )
    }

    @Serializable
    @SerialName("list")
    data class ListBlock(
        val ordered: Boolean,
        val items: List<List<RichNode>>,
    ) : RichNode

    /** Tables are rare and messy; we keep the cells so the UI can lay out a simple grid. */
    @Serializable
    @SerialName("table")
    data class Table(
        /**
         * Cells in reading order, header row first.
         *
         * Inline content rather than plain text because a cell is where the site's own posts put
         * their links — a 拼车 table's "点击查看 NQ" column is nothing but links, and flattening the
         * cell to `text()` is what turned them into unclickable prose.
         */
        val cells: List<List<List<InlineNode>>> = emptyList(),
        /**
         * The plain-string cells written by 1.2.2 and earlier, promoted by [content].
         *
         * Nothing writes it any more; it is here so those rows still decode. A `List<String>` cell
         * cannot be read as a `List<InlineNode>` one, and
         * [the app's own Room converters] lets a decode failure out into the
         * DAO rather than swallowing it — a thread cached before the upgrade would take the app down
         * on open. Delete it once no install can still be carrying a cache that old.
         */
        @SerialName("rows")
        val legacyRows: List<List<String>> = emptyList(),
    ) : RichNode {
        /** [cells], or a legacy row's strings read as unstyled text. */
        val content: List<List<List<InlineNode>>>
            get() = cells.ifEmpty { legacyRows.map { row -> row.map { listOf(InlineNode.Text(it)) } } }
    }

    @Serializable
    @SerialName("hr")
    data object Divider : RichNode
}

/** Inline pieces inside a paragraph. */
@Serializable
sealed interface InlineNode {
    @Serializable
    @SerialName("t")
    data class Text(
        val text: String,
        val style: InlineStyle = InlineStyle(),
    ) : InlineNode

    @Serializable
    @SerialName("a")
    data class Link(
        val text: String,
        val url: String,
        val style: InlineStyle = InlineStyle(),
    ) : InlineNode

    /** Emoji-sized image that must flow with the surrounding text — a forum's own stickers. */
    @Serializable
    @SerialName("sticker")
    data class Sticker(
        val url: String,
        val alt: String?,
    ) : InlineNode

    /**
     * A reply pointing at another floor, written as `@name` followed by `#3`.
     *
     * A site emits this as two ordinary anchors; folding them into one node is what lets the renderer
     * draw a tonal chip that jumps to the floor, instead of two blue links that leave the app.
     */
    @Serializable
    @SerialName("qref")
    data class QuoteRef(
        val name: String,
        val floor: String,
        val url: String,
    ) : InlineNode

    @Serializable
    @SerialName("br")
    data object LineBreak : InlineNode
}

@Serializable
data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
)
