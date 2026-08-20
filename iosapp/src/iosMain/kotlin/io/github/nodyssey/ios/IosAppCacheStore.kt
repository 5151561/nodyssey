package io.github.nodyssey.ios

import io.github.nodyssey.data.AppCacheStore
import io.github.plaza.core.AppDispatchers
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * 清除缓存 — the Caches directory, and the number the settings row reports.
 *
 * The same two moves `DefaultAppCacheStore` makes on Android, in the same order and for the same
 * reasons: Coil's disk cache is emptied through its own API rather than by deleting the directory —
 * its journal is open in this process — and everything else goes by deletion, because emptying a
 * running app's Caches directory is what the platform does to it anyway.
 *
 * @param imageCaches resolved per call rather than held: the singleton loader builds its disk cache
 *   on first touch, long after this store is constructed with the rest of the graph.
 */
class IosAppCacheStore(
    private val cacheDirectory: NSURL,
    private val dispatchers: AppDispatchers,
    private val imageCaches: () -> IosImageCaches,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : AppCacheStore {
    @OptIn(ExperimentalForeignApi::class)
    private val root: Path = requireNotNull(cacheDirectory.path).toPath()

    override suspend fun sizeBytes(): Long =
        withContext(dispatchers.io) {
            runCatching { root.totalFileSize() }.getOrDefault(0L)
        }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            val caches = imageCaches()
            caches.clearMemory()
            val imageCacheDirectory = caches.clearDisk()
            runCatching {
                fileSystem.list(root).forEach { entry ->
                    if (entry != imageCacheDirectory) fileSystem.deleteRecursively(entry, mustExist = false)
                }
            }
        }
    }

    private fun Path.totalFileSize(): Long =
        fileSystem.listOrNull(this).orEmpty().sumOf { entry ->
            val metadata = fileSystem.metadataOrNull(entry)
            when {
                metadata == null -> 0L
                metadata.isDirectory -> entry.totalFileSize()
                else -> metadata.size ?: 0L
            }
        }
}

/**
 * The image caches, as much of the loader as 清除缓存 has any business with.
 *
 * The Android counterpart is `ImageCaches` in `:app`, and it is an interface there because the class
 * beside it is unit tested against a fake. This one is a class: there is no test on this platform yet
 * to need a second implementation, and an interface with one implementor and no test is a shape
 * pretending to be a seam.
 */
class IosImageCaches(
    private val loader: () -> coil3.ImageLoader,
) {
    fun clearMemory() {
        loader().memoryCache?.clear()
    }

    /**
     * Empties the disk cache through its own API and returns the directory it kept.
     *
     * The directory comes back because it must *not* then be deleted: the cache's journal is open in
     * this process, and removing the directory underneath a live cache is a different thing from
     * telling it to empty.
     */
    fun clearDisk(): Path? {
        val cache = loader().diskCache ?: return null
        cache.clear()
        return cache.directory
    }
}
