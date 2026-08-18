package io.github.nodyssey.data

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
 * The image caches, as much of the loader as 清除缓存 has any business with.
 *
 * Narrower than the `ImageLoader` this used to be handed, and narrower on purpose: emptying two
 * caches is the whole interaction, and asking for the loader meant the only way to exercise this
 * class was to build a real one — which on Android needs a `Context`, and turned a test about files
 * on disk into a test that needed a device.
 */
interface ImageCaches {
    fun clearMemory()

    /**
     * Empties the disk cache through its own API and returns the directory it kept.
     *
     * Null when there is no disk cache. The directory comes back because it must *not* then be
     * deleted: the cache's journal is open in this process, and removing the directory underneath a
     * live cache is a different thing from telling it to empty.
     */
    fun clearDisk(): File?
}

/**
 * @param imageCaches resolved per call rather than held: the singleton loader builds its disk cache
 *   on first touch, and this store is constructed with the rest of the graph, long before any
 *   screen has asked for an image.
 */
class DefaultAppCacheStore(
    private val cacheDirectory: File,
    private val dispatchers: AppDispatchers,
    private val imageCaches: () -> ImageCaches,
) : AppCacheStore {
    override suspend fun sizeBytes(): Long =
        withContext(dispatchers.io) {
            cacheDirectory.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            val caches = imageCaches()
            caches.clearMemory()
            // Coil goes through its own API — see [ImageCaches.clearDisk].
            val imageCacheDirectory = caches.clearDisk()
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
