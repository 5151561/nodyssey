package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.nodyssey.model.TitleKeywordKind
import kotlinx.coroutines.flow.Flow

@Dao
interface UserNoteDao {
    @Query("SELECT * FROM user_notes")
    fun observeAll(): Flow<List<UserNoteEntity>>

    @Upsert
    suspend fun upsert(note: UserNoteEntity)

    @Query("DELETE FROM user_notes WHERE uid = :uid")
    suspend fun delete(uid: Long)
}

@Dao
interface TitleKeywordDao {
    @Query("SELECT * FROM title_keywords WHERE kind = :kind ORDER BY addedAtMillis ASC")
    fun observe(kind: TitleKeywordKind): Flow<List<TitleKeywordEntity>>

    @Query("SELECT * FROM title_keywords WHERE kind = :kind ORDER BY addedAtMillis ASC")
    suspend fun list(kind: TitleKeywordKind): List<TitleKeywordEntity>

    /** Ignoring a duplicate keeps the original's place in the list rather than moving it to the end. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: TitleKeywordEntity)

    @Query("DELETE FROM title_keywords WHERE kind = :kind AND keyword = :keyword")
    suspend fun delete(
        kind: TitleKeywordKind,
        keyword: String,
    )
}

@Dao
interface TrackedThreadDao {
    @Query("SELECT * FROM tracked_threads ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<TrackedThreadEntity>>

    @Query("SELECT * FROM tracked_threads")
    suspend fun all(): List<TrackedThreadEntity>

    @Query("SELECT COUNT(*) FROM tracked_threads")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM tracked_threads WHERE postId = :postId)")
    fun observeIsTracked(postId: Long): Flow<Boolean>

    @Upsert
    suspend fun upsert(thread: TrackedThreadEntity)

    @Query("DELETE FROM tracked_threads WHERE postId = :postId")
    suspend fun delete(postId: Long)

    /** Fills in what the row does not know yet; never overwrites what it does. */
    @Query(
        """
        UPDATE tracked_threads SET
            authorName = COALESCE(authorName, :authorName),
            categoryTitle = COALESCE(categoryTitle, :categoryTitle),
            categorySlug = COALESCE(categorySlug, :categorySlug)
        WHERE postId = :postId
        """,
    )
    suspend fun fillSnapshot(
        postId: Long,
        authorName: String?,
        categoryTitle: String?,
        categorySlug: String?,
    )

    /** Only ever forwards: a stale count arriving late must not re-arm a notification already sent. */
    @Query("UPDATE tracked_threads SET lastKnownCount = :count WHERE postId = :postId AND lastKnownCount < :count")
    suspend fun advance(
        postId: Long,
        count: Int,
    )
}
