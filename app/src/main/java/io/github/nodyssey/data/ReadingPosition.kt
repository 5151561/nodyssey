package io.github.nodyssey.data

import io.github.nodyssey.core.AppClock
import io.github.nodyssey.data.local.ReadingPositionDao
import io.github.nodyssey.data.local.ReadingPositionEntity

/**
 * Where a reader left a thread, so 上次阅读 has somewhere to go on the next visit.
 *
 * [floor] is the site's own label for the topmost floor on screen (`"#42"`), and it is what makes the
 * return exact rather than merely to the right page. It is nullable because the site does not label
 * every floor it serves, and a page on its own is still worth coming back to.
 */
data class ReadingPosition(
    val page: Int,
    val floor: String? = null,
)

/**
 * Where each thread was left off.
 *
 * A table of its own rather than columns on `post_read_marks`, which is where it looks like it
 * belongs, because it is not the kind of record those rows are: a place is written continuously
 * while the reader scrolls, and putting that on a table three screens observe means waking all of
 * them a few times a page. Its own table has no observers at all, so those writes wake nobody.
 *
 * How *many* of these are kept is a different question from where they live, and that answer does
 * come from the read marks: 浏览历史's 保留条数 caps both, so there is one number for how much of
 * your reading this app remembers. The trim lives with the read marks' own — see
 * [PostRepository.trimReadHistory].
 */
interface ReadingPositionStore {
    suspend fun readingPosition(postId: Long): ReadingPosition?

    suspend fun setReadingPosition(
        postId: Long,
        position: ReadingPosition,
    )
}

/**
 * The places, in the database that holds everything else about a thread.
 *
 * A row per thread rather than one serialized blob is what keeps both halves of this cheap: a place
 * is written every time the reader stops scrolling, and reading one back is a primary-key lookup
 * whatever the table holds. The blob version had to decode and re-encode every stored place on each
 * of those writes, which is fine at a hundred threads and is the wrong shape at 无上限.
 *
 * Nothing here trims. A place can only exist for a thread that was opened, and opening a thread is
 * already what runs [PostRepository.trimReadHistory] — bounding both tables there keeps this path,
 * the one that runs while somebody is scrolling, down to a single upsert.
 */
class RoomReadingPositionStore(
    private val dao: ReadingPositionDao,
    private val clock: AppClock,
) : ReadingPositionStore {
    override suspend fun readingPosition(postId: Long): ReadingPosition? =
        dao.find(postId)?.let { ReadingPosition(page = it.page, floor = it.floor) }

    override suspend fun setReadingPosition(
        postId: Long,
        position: ReadingPosition,
    ) {
        dao.upsert(
            ReadingPositionEntity(
                postId = postId,
                page = position.page,
                floor = position.floor,
                updatedAtMillis = clock.nowMillis(),
            ),
        )
    }
}

/** The store a preview or a test gets when the caller supplied none: it remembers nothing. */
object NoReadingPositions : ReadingPositionStore {
    override suspend fun readingPosition(postId: Long): ReadingPosition? = null

    override suspend fun setReadingPosition(
        postId: Long,
        position: ReadingPosition,
    ) = Unit
}
