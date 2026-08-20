package io.github.nodyssey.data

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * `AppCacheStore` itself — two suspend functions and no types — went to `commonMain` in step A7.
 * What stayed is everything that names a directory or an image loader, which is all of it.
 */
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
