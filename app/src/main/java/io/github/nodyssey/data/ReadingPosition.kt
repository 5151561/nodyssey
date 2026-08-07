package io.github.nodyssey.data

import kotlinx.serialization.Serializable

/**
 * Where a reader left a thread, so 上次阅读 has somewhere to go on the next visit.
 *
 * [floor] is the site's own label for the topmost floor on screen (`"#42"`), and it is what makes the
 * return exact rather than merely to the right page. It is nullable because the site does not label
 * every floor it serves, and a page on its own is still worth coming back to.
 */
@Serializable
data class ReadingPosition(
    val page: Int,
    val floor: String? = null,
)

/**
 * Where each thread was left off.
 *
 * Deliberately not a column on `post_read_marks`, which is where it looks like it belongs: those rows
 * are the unread baselines and are trimmed by 浏览历史's own limit, so a reader who turns that limit
 * down would silently lose their places as well. This is also the kind of record the read marks are
 * not — written continuously while scrolling rather than once per read — and DataStore takes that
 * without a write amplification on a table three screens observe.
 */
interface ReadingPositionStore {
    suspend fun readingPosition(postId: Long): ReadingPosition?

    suspend fun setReadingPosition(
        postId: Long,
        position: ReadingPosition,
    )
}

/** The store a preview or a test gets when the caller supplied none: it remembers nothing. */
object NoReadingPositions : ReadingPositionStore {
    override suspend fun readingPosition(postId: Long): ReadingPosition? = null

    override suspend fun setReadingPosition(
        postId: Long,
        position: ReadingPosition,
    ) = Unit
}
