package io.github.nodyssey.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------------------------
// 我的表情 and the GitHub sticker packs subscribed to — the device owner's, like PersonalEntities
// ---------------------------------------------------------------------------------------------
//
// Links only, never the pictures: a sticker is inserted as `![name](url)`, so the URL is the whole
// of what has to survive, and the image cache already holds whatever was drawn.

/**
 * One picture in 我的表情.
 *
 * [position] orders the group, smallest first. New stickers and 移到最前 both take one below the
 * current minimum, so neither has to renumber the rest; only a drag in 表情管理 does.
 */
@Entity(tableName = "my_stickers")
data class MyStickerEntity(
    @PrimaryKey val url: String,
    val name: String,
    val position: Long,
    val addedAtMillis: Long,
)

/**
 * One subscribed GitHub repository.
 *
 * [pinnedSha] is the commit every inserted link names. [latestSha] is what the last check found on
 * [ref]; when it differs, the folders below carry the file lists of that commit in
 * `pendingFiles` until the reader says 更新 — the pack never changes under them on its own.
 */
@Entity(tableName = "sticker_repos")
data class StickerRepoEntity(
    /** `owner/repo`, as GitHub spells it. */
    @PrimaryKey val slug: String,
    val ref: String,
    val pinnedSha: String,
    val latestSha: String?,
    val checkedAtMillis: Long,
    val position: Long,
    /**
     * How many image folders the repository has — 订了 2 / 46 个文件夹 — at the commit last listed:
     * the pinned one when subscribing, the branch's newer one once a check has found it. Null on a
     * row from before v20 until a check fills it in.
     */
    val folderCount: Int? = null,
)

/**
 * One folder of a subscribed repository — one tab of the panel.
 *
 * [files] is the folder's image paths at the pinned commit, repository-relative and newline-joined:
 * read whole, never queried into, and a table of its own would be a few hundred rows per tab for
 * nothing. [pendingFiles] is the same list at the newer commit, null when there is none.
 */
@Entity(tableName = "sticker_folders", primaryKeys = ["repoSlug", "path"])
data class StickerFolderEntity(
    val repoSlug: String,
    /** Repository-relative, no trailing slash; empty for the repository root. */
    val path: String,
    val files: String,
    val pendingFiles: String?,
    val hidden: Boolean,
    val position: Long,
)
