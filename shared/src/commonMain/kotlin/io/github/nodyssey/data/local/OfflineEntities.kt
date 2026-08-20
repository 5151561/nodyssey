package io.github.nodyssey.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.nodyssey.model.PostContent

/*
 * What this device has stored for reading with the network off.
 *
 * Deliberately three tables of its own rather than a flag on `post_details`. That cache is a
 * *window* — the pages a reader happened to scroll through — and `saveThreadPage(replacesWindow =
 * true)` throws the rest of it away on every refresh, which is exactly what opening a downloaded
 * thread while online does. A pin bit on those rows would therefore be a promise the next refresh
 * quietly breaks. These rows are only ever written by the download engine and only ever removed by
 * the reader, by the retention sweep, or by 清空离线内容.
 *
 * The two caches meet in one place: [io.github.nodyssey.data.OfflineFirstPostRepository] reads a
 * page from here when the site cannot be reached, and stores it into `post_details` as though it
 * had arrived from the network — carrying the download's own timestamp, so a copy from last week
 * does not read as a page fetched a moment ago.
 */

/**
 * The four states a row in the queue can be in; [OfflineThreadEntity.status] holds one.
 *
 * Public rather than `internal` because the column is written and read by a repository, and since
 * step A6 that repository is in another module.
 */
object OfflineStatus {
    const val QUEUED = 0
    const val DOWNLOADING = 1
    const val DOWNLOADED = 2
    const val FAILED = 3
}

/**
 * One downloaded thread: its opening post, how much of it is here, and where it is in the queue.
 *
 * [storedCommentCount] and [remoteCommentCount] are two different counts on purpose, and their
 * difference is the whole of 「离线版落后 N 条回复」: the first is what this copy actually contains,
 * the second is what the site last said the thread has. The second is null until something tells
 * us — the collection list does, once per load — and null is not zero.
 */
@Entity(tableName = "offline_threads")
data class OfflineThreadEntity(
    @PrimaryKey val postId: Long,
    val title: String,
    /** Null while queued, and for a thread whose page 1 has not been stored yet. */
    val body: PostContent?,
    val totalPages: Int,
    val storedCommentCount: Int,
    val remoteCommentCount: Int?,
    val status: Int,
    /** Share of this thread already fetched, or null while it is queued behind others. */
    val progress: Float?,
    /** [io.github.nodyssey.data.OfflineFailure] ordinal, non-null only in [OfflineStatus.FAILED]. */
    val failure: Int?,
    /** Serialized size of the body and every stored comment. Images are counted per file. */
    val textBytes: Long,
    /** See [PostDetailEntity.collected] — same three-valued claim, stored for the same reason. */
    val collected: Boolean? = null,
    val collectionCount: Int? = null,
    val isAwarded: Boolean? = null,
    val queuedAtMillis: Long,
    /** When the copy was completed, which is what 保留期限 counts from. Null until it is. */
    val downloadedAtMillis: Long?,
)

/**
 * One stored comment, keyed exactly like [CommentEntity] — see its note on why position, not id.
 *
 * A separate table from `post_comments` rather than a shared one: these two are written by
 * different things with different lifetimes, and a foreign key onto both would make the reader's
 * scroll able to evict what the download engine promised to keep.
 */
@Entity(
    tableName = "offline_comments",
    primaryKeys = ["postId", "page", "position"],
    foreignKeys = [
        ForeignKey(
            entity = OfflineThreadEntity::class,
            parentColumns = ["postId"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class OfflineCommentEntity(
    val postId: Long,
    val page: Int,
    val position: Int,
    val content: PostContent,
)

/**
 * One image this thread needs, and the file it was stored in.
 *
 * Keyed by (postId, url) rather than by url alone so two threads embedding the same image each own
 * a row, and deleting one of them cannot take the other's picture away. The *file* is shared —
 * [fileName] is derived from the url — so the size sums count distinct files, never rows, and a
 * file is only unlinked once no row is left pointing at it.
 */
@Entity(
    tableName = "offline_images",
    primaryKeys = ["postId", "url"],
    foreignKeys = [
        ForeignKey(
            entity = OfflineThreadEntity::class,
            parentColumns = ["postId"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fileName")],
)
data class OfflineImageEntity(
    val postId: Long,
    val url: String,
    val fileName: String,
    val bytes: Long,
)
