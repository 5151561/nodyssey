package io.github.nsreader.model

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
)

data class PostListPage(
    val posts: List<PostSummary>,
    val page: Int,
    val hasNextPage: Boolean,
)

/** The opening post plus the comments on the requested page. */
data class PostDetail(
    val postId: Long,
    val title: String,
    val body: PostContent,
    val comments: List<PostContent>,
    val page: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
)

/** A post body or a single comment — both use the same markup on NodeSeek. */
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
)

/** Block-level pieces of rendered post markup. */
sealed interface RichNode {
    data class Paragraph(val inlines: List<InlineNode>) : RichNode

    data class Heading(val level: Int, val inlines: List<InlineNode>) : RichNode

    /** An image on its own line, rendered full width rather than inline with text. */
    data class BlockImage(val url: String, val alt: String?) : RichNode

    data class CodeBlock(val code: String, val language: String?) : RichNode

    data class Quote(val children: List<RichNode>) : RichNode

    data class ListBlock(val ordered: Boolean, val items: List<List<RichNode>>) : RichNode

    /** Tables are rare and messy; we keep the cells so the UI can lay out a simple grid. */
    data class Table(val rows: List<List<String>>) : RichNode

    data object Divider : RichNode
}

/** Inline pieces inside a paragraph. */
sealed interface InlineNode {
    data class Text(val text: String, val style: InlineStyle = InlineStyle()) : InlineNode

    data class Link(val text: String, val url: String, val style: InlineStyle = InlineStyle()) :
        InlineNode

    /** Emoji-sized image that must flow with the surrounding text (NodeSeek stickers). */
    data class Sticker(val url: String, val alt: String?) : InlineNode

    data object LineBreak : InlineNode
}

data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
)
