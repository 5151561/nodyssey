package io.github.nodyssey.core

import io.github.nodyssey.model.PostContent
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode

/** 同时保留回复目标和直接回复，不复制正文或改变楼层顺序。 */
class CommentReplies internal constructor(
    private val repliesByComment: List<List<Int>>,
    private val targetsByComment: List<CommentReplyTarget?>,
) {
    fun directRepliesOf(index: Int): List<Int> = repliesByComment.getOrNull(index).orEmpty()

    fun replyTargetOf(index: Int): CommentReplyTarget? = targetsByComment.getOrNull(index)
}

/** 目标尚未加载或指向楼主正文时没有评论索引，楼层仍可用于跳转。 */
data class CommentReplyTarget(val floor: Int, val commentIndex: Int?)

/**
 * 从正文开头的明确引用整理双向关系。目标尚未加载时保留入口，不计入直接回复数量。
 * 只连接更早的楼层；置顶评论保留回复目标，但不作为其他评论的回复预览。
 */
fun buildCommentReplies(postId: Long, comments: List<PostContent>): CommentReplies {
    val floors = comments.map { NodeSeekSite.parseFloorNumber(it.floor) }
    val indicesByFloor = comments.indices
        .filter { (floors[it] ?: 0) > 0 }
        .groupBy { floors[it] }
    val replies = List(comments.size) { mutableListOf<Int>() }
    val targets = MutableList<CommentReplyTarget?>(comments.size) { null }
    comments.forEachIndexed { index, comment ->
        val ownFloor = floors[index]
        val target = comment.replyFloor(postId)
        if (ownFloor == null || indicesByFloor[ownFloor]?.size != 1 || target == null || target >= ownFloor) {
            return@forEachIndexed
        }
        val targetIndices = indicesByFloor[target]
        if (targetIndices != null && targetIndices.size != 1) return@forEachIndexed
        val targetIndex = targetIndices?.singleOrNull()
        targets[index] = CommentReplyTarget(target, targetIndex)
        if (!comment.isPinned && targetIndex != null) replies[targetIndex] += index
    }
    return CommentReplies(replies, targets)
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
                return floor.takeIf { it >= 0 && it == NodeSeekSite.parseFloorNumber(reference.floor) }
            }

            else -> return null
        }
    }
}
