package io.github.nodyssey.data

/**
 * 清除缓存 — the files under the app's cache directory, and the number the settings row reports.
 *
 * The database has bounded itself since the beginning: [OfflineFirstPostRepository] trims to
 * [PostRepository.MAX_CACHED_THREADS] threads on every write, and 浏览历史's own limit trims the
 * marks and positions. That covers the *text*, which is around a megabyte. What grows is what sits
 * beside it on disk and belongs to no repository at all — Coil's image cache, which the default
 * loader is free to run to 250 MB, WebView's HTTP cache, and an update APK whose install never
 * reported success. 清除缓存 cleared only the database, so none of that ever went anywhere and the
 * figure in system settings kept climbing whatever the user did in the app.
 */
interface AppCacheStore {
    /** Bytes currently held under the cache directory. */
    suspend fun sizeBytes(): Long

    /** Empties everything [sizeBytes] counts. */
    suspend fun clear()

    /**
     * Drops one picture out of the image caches, leaving the rest of them alone.
     *
     * Not a smaller 清除缓存 but the answer to a different question. The image cache deliberately
     * stops asking the server about an avatar for a week, because its address is fixed for the life
     * of the account and checking every few hours costs a round trip per face in a list. That is a
     * fair trade for a stranger's new picture and a bad one for the reader's own, so the upload path
     * says which address it just changed instead of waiting the week out.
     */
    suspend fun evictImage(url: String)
}
