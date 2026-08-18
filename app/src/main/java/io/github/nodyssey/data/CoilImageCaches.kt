package io.github.nodyssey.data

import coil3.ImageLoader
import java.io.File

/** [ImageCaches] on the singleton Coil loader — the two caches it owns and nothing else. */
class CoilImageCaches(private val loader: ImageLoader) : ImageCaches {
    override fun clearMemory() {
        loader.memoryCache?.clear()
    }

    override fun clearDisk(): File? =
        loader.diskCache?.let { cache ->
            cache.clear()
            File(cache.directory.toString())
        }
}
