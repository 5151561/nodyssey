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
 * Kept in DataStore rather than as a column on `post_read_marks`, which is where it looks like it
 * belongs, because it is not the kind of record those rows are: a place is written continuously while
 * the reader scrolls, and putting that on a table three screens observe means waking all of them a
 * few times a page. How *many* of these are kept is a different question from where they live, and
 * that answer does come from the read marks — the implementation keeps as many places as 浏览历史
 * keeps threads, so there is one number for how much of your reading this app remembers.
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
