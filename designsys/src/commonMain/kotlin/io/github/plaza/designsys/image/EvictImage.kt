package io.github.plaza.designsys.image

import coil3.ImageLoader

/**
 * Drops one picture out of both caches, so the next request for it goes back to the server.
 *
 * For an address the app has been told to stop asking about — see [LongLivedImageCacheStrategy] —
 * this is the only thing that brings a new picture in early.
 *
 * The memory cache is swept rather than asked. Its keys carry the size and scale each request was
 * made at, so one avatar drawn in a list row and again on a profile header is two entries under the
 * same URL, and removing `Key(url)` alone would find neither of them.
 */
fun ImageLoader.evictImage(url: String) {
    memoryCache?.let { cache ->
        // Copied out of the live key set before anything is removed from it.
        cache.keys.filter { it.key == url }.forEach(cache::remove)
    }
    diskCache?.remove(url)
}
