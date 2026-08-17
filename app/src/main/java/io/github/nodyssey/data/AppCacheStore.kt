package io.github.nodyssey.data

import coil3.ImageLoader
import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
}

/**
 * @param imageLoader resolved per call rather than held: the singleton loader builds its disk cache
 *   on first touch, and this store is constructed with the rest of the graph, long before any
 *   screen has asked for an image.
 */
class DefaultAppCacheStore(
    private val cacheDirectory: File,
    private val dispatchers: AppDispatchers,
    private val imageLoader: () -> ImageLoader,
) : AppCacheStore {
    override suspend fun sizeBytes(): Long =
        withContext(dispatchers.io) {
            cacheDirectory.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            val loader = imageLoader()
            loader.memoryCache?.clear()
            // Coil goes through its own API: its journal is open in this process, and deleting the
            // directory underneath a live cache is a different thing from telling it to empty.
            val imageCacheDirectory =
                loader.diskCache?.let { cache ->
                    cache.clear()
                    File(cache.directory.toString())
                }
            // Everything else goes by deletion. WebView's cache has no API that does not mean
            // constructing a WebView on the main thread from the data layer, and `updates` is ours
            // to begin with. Deleting a running app's cache files is what the platform's own
            // 清除缓存 does, so whatever lives here already has to survive it.
            cacheDirectory
                .listFiles()
                ?.filter { it != imageCacheDirectory }
                ?.forEach { it.deleteRecursively() }
        }
    }
}
