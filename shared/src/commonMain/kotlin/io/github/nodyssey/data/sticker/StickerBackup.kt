package io.github.nodyssey.data.sticker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 表情管理 › 备份: 我的, the subscriptions and the panel's group order, as one piece of JSON a reader
 * can keep anywhere and paste back on another device. Pictures are not in it — 我的 holds links, and
 * a subscription is re-read from GitHub at the commit it was pinned to.
 */
@Serializable
internal data class StickerBackup(
    /** Required, and named for this app: it is what tells a backup from any other JSON on the clipboard. */
    @SerialName("nodyssey_stickers") val version: Int,
    val mine: List<Mine> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
) {
    @Serializable
    data class Mine(val url: String, val name: String)

    @Serializable
    data class Subscription(
        val repo: String,
        val ref: String,
        val sha: String,
        val folders: List<String>,
        val hiddenFolders: List<String> = emptyList(),
    )

    companion object {
        const val VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(backup: StickerBackup): String = json.encodeToString(serializer(), backup)

        /** Null when [text] is not a backup this app wrote — a list of links, say, or anything else. */
        fun decode(text: String): StickerBackup? =
            runCatching { json.decodeFromString(serializer(), text.trim()) }
                .getOrNull()
                ?.takeIf { it.version in 1..VERSION }
    }
}

/** What 导入 managed. A subscription that could not be read again is counted, not fatal. */
data class StickerImportResult(
    val stickers: Int,
    val folders: Int,
    val failedRepos: List<String>,
)
