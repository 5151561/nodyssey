package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {
    @Query("SELECT * FROM my_stickers ORDER BY position ASC")
    fun observeMine(): Flow<List<MyStickerEntity>>

    @Query("SELECT * FROM my_stickers ORDER BY position ASC")
    suspend fun listMine(): List<MyStickerEntity>

    @Query("SELECT MIN(position) FROM my_stickers")
    suspend fun minPosition(): Long?

    /** A link already in 我的 keeps its name and its place. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMine(stickers: List<MyStickerEntity>): List<Long>

    /**
     * Inserts [stickers] just above the current first sticker, first one first — the minimum read
     * and the insert in one transaction, so two adds at once cannot both take the same places.
     * @return [insertMine]'s row ids, -1 for a link that was already there.
     */
    @Transaction
    suspend fun insertAtFront(stickers: List<MyStickerEntity>): List<Long> {
        val top = minPosition() ?: 0L
        return insertMine(stickers.mapIndexed { index, sticker -> sticker.copy(position = top - stickers.size + index) })
    }

    @Query("UPDATE my_stickers SET name = :name WHERE url = :url")
    suspend fun rename(
        url: String,
        name: String,
    )

    @Query("UPDATE my_stickers SET position = :position WHERE url = :url")
    suspend fun setPosition(
        url: String,
        position: Long,
    )

    @Query("DELETE FROM my_stickers WHERE url IN (:urls)")
    suspend fun deleteMine(urls: List<String>)

    @Transaction
    suspend fun reorderMine(urls: List<String>) {
        urls.forEachIndexed { index, url -> setPosition(url, index.toLong()) }
    }

    @Query("SELECT * FROM sticker_repos ORDER BY position ASC")
    fun observeRepos(): Flow<List<StickerRepoEntity>>

    @Query("SELECT * FROM sticker_repos ORDER BY position ASC")
    suspend fun listRepos(): List<StickerRepoEntity>

    @Query("SELECT * FROM sticker_repos WHERE slug = :slug")
    suspend fun repo(slug: String): StickerRepoEntity?

    @Query("SELECT MAX(position) FROM sticker_repos")
    suspend fun maxRepoPosition(): Long?

    @Upsert
    suspend fun upsertRepo(repo: StickerRepoEntity)

    @Query("SELECT * FROM sticker_folders ORDER BY repoSlug ASC, position ASC")
    fun observeFolders(): Flow<List<StickerFolderEntity>>

    @Query("SELECT * FROM sticker_folders WHERE repoSlug = :slug ORDER BY position ASC")
    suspend fun folders(slug: String): List<StickerFolderEntity>

    @Upsert
    suspend fun upsertFolders(folders: List<StickerFolderEntity>)

    @Query("DELETE FROM sticker_folders WHERE repoSlug = :slug AND path NOT IN (:keep)")
    suspend fun deleteFoldersExcept(
        slug: String,
        keep: List<String>,
    )

    @Query("UPDATE sticker_folders SET hidden = :hidden WHERE repoSlug = :slug AND path = :path")
    suspend fun setHidden(
        slug: String,
        path: String,
        hidden: Boolean,
    )

    @Query("UPDATE sticker_folders SET position = :position WHERE repoSlug = :slug AND path = :path")
    suspend fun setFolderPosition(
        slug: String,
        path: String,
        position: Long,
    )

    @Transaction
    suspend fun reorderFolders(
        slug: String,
        paths: List<String>,
    ) {
        paths.forEachIndexed { index, path -> setFolderPosition(slug, path, index.toLong()) }
    }

    /**
     * Replaces the folder set of [repo] wholesale — a subscription, or 换文件夹. [folders] is the
     * whole set: whatever the repository had that is not in it is removed.
     */
    @Transaction
    suspend fun replaceSubscription(
        repo: StickerRepoEntity,
        folders: List<StickerFolderEntity>,
    ) {
        upsertRepo(repo)
        deleteFoldersExcept(repo.slug, folders.map { it.path })
        upsertFolders(folders)
    }

    /** @return 0 when [slug] is gone or no longer pinned at [pinnedSha]. */
    @Query(
        "UPDATE sticker_repos SET latestSha = :latestSha, checkedAtMillis = :checkedAtMillis " +
            "WHERE slug = :slug AND pinnedSha = :pinnedSha",
    )
    suspend fun setLatest(
        slug: String,
        pinnedSha: String,
        latestSha: String?,
        checkedAtMillis: Long,
    ): Int

    @Query("UPDATE sticker_folders SET pendingFiles = :pendingFiles WHERE repoSlug = :slug AND path = :path")
    suspend fun setPending(
        slug: String,
        path: String,
        pendingFiles: String?,
    )

    @Query("UPDATE sticker_folders SET pendingFiles = NULL WHERE repoSlug = :slug")
    suspend fun clearPending(slug: String)

    /**
     * What an update check found, written over whatever the rows hold *now* rather than over the
     * snapshot the check started from: the check spends seconds on the network, and in that time
     * the reader may have unsubscribed, hidden or reordered a folder, or applied an update.
     *
     * Nothing is written unless [slug] is still subscribed and still pinned at [pinnedSha] — the
     * commit the check compared against. Only `latestSha`, `checkedAtMillis` and `pendingFiles`
     * are touched:
     * - [latestSha] null (the branch is where the pin is) clears every folder's pending list;
     * - otherwise, with [filesAtLatest] (folder path → newline-joined files at [latestSha]), every
     *   folder subscribed *now* gets its list from it. A folder missing from it has vanished
     *   upstream and gets an empty list — unless [truncated], when GitHub may simply not have said,
     *   and the folder is left without a pending list rather than condemned on no evidence;
     * - [filesAtLatest] null leaves the folders as they are.
     */
    @Transaction
    suspend fun recordCheck(
        slug: String,
        pinnedSha: String,
        latestSha: String?,
        checkedAtMillis: Long,
        filesAtLatest: Map<String, String>?,
        truncated: Boolean,
    ) {
        if (setLatest(slug, pinnedSha, latestSha, checkedAtMillis) == 0) return
        when {
            latestSha == null -> clearPending(slug)

            filesAtLatest != null -> folders(slug).forEach { folder ->
                setPending(slug, folder.path, filesAtLatest[folder.path] ?: if (truncated) null else "")
            }
        }
    }

    @Query("UPDATE sticker_repos SET folderCount = :count WHERE slug = :slug")
    suspend fun setFolderCount(
        slug: String,
        count: Int,
    )

    @Query("DELETE FROM sticker_repos WHERE slug = :slug")
    suspend fun deleteRepoRow(slug: String)

    @Query("DELETE FROM sticker_folders WHERE repoSlug = :slug")
    suspend fun deleteRepoFolders(slug: String)

    @Transaction
    suspend fun unsubscribe(slug: String) {
        deleteRepoFolders(slug)
        deleteRepoRow(slug)
    }
}
