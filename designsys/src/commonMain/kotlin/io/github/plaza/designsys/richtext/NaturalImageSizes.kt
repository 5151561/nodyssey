package io.github.plaza.designsys.richtext

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.IntSize

/**
 * The size each image turned out to be, by URL, for as long as the process lives.
 *
 * Sizes only — two ints per entry, never pixels — so this is free to outlive any one screen and to
 * hold more entries than a reader will scroll past. Coil's own memory cache is keyed by request
 * *including* the size asked for, which is the one thing an image cannot say before it has been
 * measured once; this is that missing first answer. See [BlockImage] for what it buys.
 *
 * A hand-written LRU rather than `android.util.LruCache`, and its own file rather than a private val
 * beside the composable that reads it: what is stored here is a measurement, and nothing about
 * remembering a measurement is an Android question. `LinkedHashMap` keeps insertion order in common
 * Kotlin, which is the whole mechanism — a read re-inserts, so the least recently used key is always
 * the first one.
 *
 * One behaviour is deliberately *not* carried over: `android.util.LruCache` synchronises every
 * method and this does not. Both call sites are inside composition — the `remember` that seeds
 * [BlockImage] and the `onSuccess` Coil delivers back into it. A caller reaching this from anywhere
 * else has to bring its own lock.
 */
internal object NaturalImageSizes {
    private val sizes = LinkedHashMap<String, IntSize>()

    operator fun get(url: String): IntSize? = sizes.remove(url)?.also { sizes[url] = it }

    fun put(
        url: String,
        size: IntSize,
    ) {
        sizes.remove(url)
        sizes[url] = size
        while (sizes.size > MAX_ENTRIES) {
            sizes.remove(sizes.keys.first())
        }
    }

    fun clear() = sizes.clear()

    private const val MAX_ENTRIES = 512
}

/**
 * Forgets every measured image size.
 *
 * For tests: the cache is process-wide, and one JVM runs many of them. A test whose image is a
 * different size from an earlier test's image at the same URL would otherwise lay out against the
 * earlier one — the same hazard `SingletonImageLoader.reset()` exists for.
 */
@VisibleForTesting
fun resetNaturalImageSizes(): Unit = NaturalImageSizes.clear()
