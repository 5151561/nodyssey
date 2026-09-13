package io.github.nodyssey.ui.postdetail

import io.github.nodyssey.model.PostContent

/** 正常评论沿用稳定编号；分页移动或置顶造成重复时，保留每一行并避免 LazyColumn 键冲突。 */
internal fun List<PostContent>.commentKeys(): List<String> {
    val bases = mapIndexed { index, comment ->
        comment.commentId?.let { "comment-$it" } ?: comment.floor?.let { "floor-$it" } ?: "comment-index-$index"
    }
    val counts = bases.groupingBy { it }.eachCount()
    val occurrences = mutableMapOf<String, Int>()
    return bases.map { base ->
        if (counts[base] == 1) {
            base
        } else {
            val occurrence = occurrences.getOrElse(base) { 0 }
            occurrences[base] = occurrence + 1
            "$base-occurrence-$occurrence"
        }
    }
}
