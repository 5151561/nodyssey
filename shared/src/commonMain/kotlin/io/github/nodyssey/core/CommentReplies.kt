package io.github.nodyssey.core

import io.github.nodyssey.model.PostContent
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode

/** 只保存直接回复的原始评论索引，不复制正文或改变楼层顺序。 */
class CommentReplies internal constructor(
    private val repliesByComment: List<List<Int>>,
) {
    fun directRepliesOf(index: Int): List<Int> = repliesByComment.getOrNull(index).orEmpty()
}

/**
 * 从正文开头的明确引用整理直接回复。目标楼层尚未加载、楼层重复或引用不明确时不计入预览。
 * 只连接更早的楼层；置顶评论独立显示，不作为其他评论的回复预览。
 */
fun buildCommentReplies(postId: Long, comments: List<PostContent>): CommentReplies {
    val floors = comments.map { NodeSeekSite.parseFloorNumber(it.floor) }
    val indicesByFloor = comments.indices
        .filter { (floors[it] ?: 0) > 0 }
        .groupBy { floors[it] }
    val replies = List(comments.size) { mutableListOf<Int>() }
    comments.forEachIndexed { index, comment ->
        val ownFloor = floors[index]
        val target = comment.replyFloor(postId)
        if (comment.isPinned || ownFloor == null || indicesByFloor[ownFloor]?.size != 1 || target == null || target >= ownFloor) {
            return@forEachIndexed
        }
        indicesByFloor[target]?.singleOrNull()?.let { replies[it] += index }
    }
    return CommentReplies(replies)
}

private fun PostContent.replyFloor(postId: Long): Int? {
    var blocks = nodes
    // 引用按钮生成的正文以 blockquote 开头，回复按钮生成的正文以普通段落开头。
    while (true) {
        when (val first = blocks.firstOrNull()) {
            is RichNode.Quote -> blocks = first.children

            is RichNode.Paragraph -> {
                val inlines = first.inlines.dropWhile {
                    it is InlineNode.LineBreak || (it is InlineNode.Text && it.text.isBlank())
                }
                if (inlines.isEmpty()) {
                    blocks = blocks.drop(1)
                    continue
                }
                val reference = inlines.firstOrNull() as? InlineNode.QuoteRef ?: return null
                // 同一段落引用多个楼层时，没有可靠的唯一父评论。
                if (inlines.filterIsInstance<InlineNode.QuoteRef>().distinct().size != 1) return null
                val floor = NodeSeekSite.referencedFloor(postId, reference.url) ?: return null
                return floor.takeIf { it > 0 && it == NodeSeekSite.parseFloorNumber(reference.floor) }
            }

            else -> return null
        }
    }
}
