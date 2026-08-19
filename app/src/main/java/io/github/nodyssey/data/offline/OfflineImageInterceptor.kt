package io.github.nodyssey.data.offline

import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageResult

/**
 * Draws a downloaded picture from disk instead of asking for it again.
 *
 * First in the chain, ahead of the data-usage policy, and that order is the point: a stored image
 * costs no bytes, so 仅 Wi-Fi 加载图片 has nothing to defer and a thread the reader downloaded
 * precisely so they could read it on the train must not come up blank on the train.
 *
 * The memory cache key stays the URL. Rewriting the request's data would otherwise give the same
 * picture two entries — one under the URL from before it was downloaded, one under the file — and
 * halve the cache for every image in a stored thread.
 */
class OfflineImageInterceptor(
    private val files: OfflineFileStore,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data.toString()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return chain.proceed()
        val file = files.fileOf(url) ?: return chain.proceed()
        val request =
            chain.request
                .newBuilder()
                .data(file)
                .memoryCacheKey(MemoryCache.Key(url))
                .build()
        return chain.withRequest(request).proceed()
    }
}
