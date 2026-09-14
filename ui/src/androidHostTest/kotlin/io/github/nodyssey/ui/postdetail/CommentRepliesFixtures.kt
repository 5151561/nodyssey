package io.github.nodyssey.ui.postdetail

import io.github.nodyssey.model.PostContent
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode

internal fun replyComment(floor: Int, parent: Int? = null): PostContent = PostContent(
    commentId = floor.toLong(),
    floor = "#$floor",
    authorName = "reader$floor",
    authorUid = floor.toLong(),
    avatarUrl = null,
    isOriginalPoster = floor == 0,
    badges = emptyList(),
    createdAtText = null,
    createdAtTitle = null,
    categoryTitle = null,
    nodes = listOf(
        RichNode.Paragraph(
            buildList {
                if (parent != null) add(InlineNode.QuoteRef("reader$parent", "#$parent", "/post-42-1#$parent"))
                add(InlineNode.Text("comment $floor"))
            },
        ),
    ),
)

internal fun replyChain(count: Int): List<PostContent> = (1..count).map { replyComment(it, (it - 1).takeIf { floor -> floor > 0 }) }
