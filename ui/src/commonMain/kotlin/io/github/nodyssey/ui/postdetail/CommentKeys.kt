package io.github.nodyssey.ui.postdetail

import io.github.nodyssey.model.PostContent

/**
 * A stable list key per comment, built from where Room keeps the comment's identity.
 *
 * `CommentEntity`'s primary key is `(postId, page, position)` — NodeSeek does not number every
 * comment in the markup — and neither `toSnapshot` nor the extend path dedupes by `commentId`. So
 * one floor can legitimately appear twice in the loaded window: a deletion above it moves it from
 * page 3 to page 2 while page 3 is still held, and a pinned floor is served on its own page as
 * well as in place. The page therefore goes into the key, which separates those two copies without
 * either of them depending on the other existing.
 *
 * What this exists to avoid is a key that changes when something *else* in the list changes. A
 * scheme that only disambiguates on collision renames the first copy the moment a second arrives:
 * LazyColumn rebuilds that row, and every string held against it — an expanded reply card, a test
 * tag, a scroll waiting to find it again — is suddenly pointing at nothing, with no error anywhere.
 *
 * [pages] is index-aligned with the receiver, as `PostDetailUiState.commentPages` is; a short list
 * (an isolated sublist, a preview) simply leaves the page out of those keys.
 */
internal fun List<PostContent>.commentKeys(pages: List<Int> = emptyList()): List<String> {
    val seen = mutableMapOf<String, Int>()
    return mapIndexed { index, comment ->
        val page = pages.getOrNull(index)?.let { "-p$it" }.orEmpty()
        val base = comment.commentId?.let { "comment-$it$page" }
            ?: comment.floor?.let { "floor-$it$page" }
            ?: "comment-index-$index"
        // Same id twice on the same page is not something the site does; the counter is here so a
        // reshaped page cannot crash the list, and it counts forwards so earlier rows keep theirs.
        val occurrence = seen.getOrElse(base) { 0 }
        seen[base] = occurrence + 1
        if (occurrence == 0) base else "$base-$occurrence"
    }
}
