package io.github.nsreader.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row in a topic list. */
data class PostSummary(
    val postId: Long,
    val title: String,
    val authorName: String,
    val authorUid: Long?,
    val avatarUrl: String?,
    val categoryTitle: String?,
    val categorySlug: String?,
    val viewCount: Int?,
    val commentCount: Int?,
    val lastActiveText: String?,
    val lastActiveTitle: String?,
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    /** The reader level a locked post demands, when the list shows one next to the lock icon. */
    val lockLevel: Int? = null,
)

data class PostListPage(
    val posts: List<PostSummary>,
    val page: Int,
    val hasNextPage: Boolean,
    /**
     * Highest page the pager offers, or [page] when it offers none.
     *
     * The feed ignores this — it scrolls — but the lists the site pages by number (curated threads,
     * the moderation log) need the total to draw "1 … 18", and it is only knowable from the markup we
     * are already holding.
     */
    val totalPages: Int = page,
)

/**
 * The comments on one page of a thread, plus the opening post if that page carried it.
 *
 * [body] is null on page 2 and later, because NodeSeek renders the opening post only on page 1.
 * That distinction is not cosmetic: the cache has to tell "this page did not include the body" apart
 * from "the body is empty", or appending page 2 would overwrite the stored body with nothing.
 */
data class PostDetail(
    val postId: Long,
    val title: String,
    val body: PostContent?,
    val comments: List<PostContent>,
    val page: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
)

/**
 * A thread as currently held in the database — what the detail screen renders, online or not.
 *
 * Unlike [PostDetail] this is not one fetch: [comments] accumulates every page read so far, which is
 * how the thread reads as one scroll on a phone.
 */
data class ThreadSnapshot(
    val postId: Long,
    val title: String,
    val body: PostContent?,
    val comments: List<PostContent>,
    val loadedPages: Int,
    val totalPages: Int,
    val cachedAtMillis: Long,
    /** The site page each comment came from, index-aligned with [comments]. */
    val commentPages: List<Int> = emptyList(),
) {
    val hasNextPage: Boolean get() = loadedPages < totalPages
}

/**
 * A post body or a single comment — both use the same markup on NodeSeek.
 *
 * Rich content is stored in the database as a JSON blob rather than as normalised tables: nothing
 * queries *into* a post body, so columns per node type would be cost without benefit.
 *
 * Every [Serializable] type below therefore carries an explicit, short [SerialName]. The discriminator
 * ends up inside rows that outlive the install, so it must not be a Kotlin class name that a refactor
 * could silently change and make old rows unreadable.
 */
@Serializable
data class PostContent(
    val commentId: Long?,
    val floor: String?,
    val authorName: String,
    val authorUid: Long?,
    val avatarUrl: String?,
    val isOriginalPoster: Boolean,
    val badges: List<String>,
    val createdAtText: String?,
    val createdAtTitle: String?,
    val categoryTitle: String?,
    val nodes: List<RichNode>,
    /*
     * Defaults are load-bearing on the two fields below: rows serialized before they existed have
     * no such keys, and `encodeDefaults = false` means rows written now omit them when false/null.
     */
    /** The header's `edited Xmin ago` marker (additions.md §1.4) — posts and comments alike. */
    val isEdited: Boolean = false,
    /** The marker's own text, kept verbatim for the 已编辑 row's accessibility label. */
    val editedAtText: String? = null,
)

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

    @Serializable
    @SerialName("code")
    data class CodeBlock(
        val code: String,
        val language: String?,
    ) : RichNode

    @Serializable
    @SerialName("quote")
    data class Quote(
        val children: List<RichNode>,
    ) : RichNode

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
        val rows: List<List<String>>,
    ) : RichNode

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

    /** Emoji-sized image that must flow with the surrounding text (NodeSeek stickers). */
    @Serializable
    @SerialName("sticker")
    data class Sticker(
        val url: String,
        val alt: String?,
    ) : InlineNode

    /**
     * A reply pointing at another floor, which NodeSeek writes as `@name` followed by `#3`.
     *
     * The site emits two ordinary anchors; folding them into one node is what lets the renderer draw
     * a tonal chip that jumps to the floor, instead of two blue links that leave the app.
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
