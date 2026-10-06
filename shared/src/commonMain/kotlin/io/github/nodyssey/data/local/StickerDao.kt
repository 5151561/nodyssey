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

    /** Replaces the folder set of [repo] wholesale — a subscription, or 换文件夹. */
    @Transaction
    suspend fun replaceSubscription(
        repo: StickerRepoEntity,
        folders: List<StickerFolderEntity>,
    ) {
        upsertRepo(repo)
        deleteFoldersExcept(repo.slug, folders.map { it.path })
        upsertFolders(folders)
    }

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
