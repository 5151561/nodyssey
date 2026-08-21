package io.github.plaza.designsys.image

import coil3.network.CacheStrategy
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Lets a picture whose address outlives its content be served from disk for [maxAge], whatever the
 * server said about it.
 *
 * The case this exists for is an avatar. A forum serves one from an address built out of the
 * account's id, so the address is fixed for the life of the account while the picture behind it is
 * not — which is why the app asks a cache strategy that honours `Cache-Control` rather than one that
 * hands back whatever is on disk and never asks again.
 *
 * Honouring it exactly turned out to cost more than it was worth. The site sends `max-age=14400` on
 * an avatar, but the response has been sitting in a CDN for part of that already — one measured on
 * 2026-08-22 arrived with `age: 8001`, leaving under two hours of it — so every few hours *every*
 * face in a list goes stale at once. Each one then costs a round trip to be told nothing changed,
 * fifty of them to open a feed, five at a time through one host's connection limit.
 *
 * So the freshness this keeps is the app's own policy rather than the site's answer: [delegate] does
 * all the work, and what is stored for a [isLongLived] address is stamped with [maxAge] on the way
 * into the cache. The trade is stated plainly — somebody else's new picture can take that long to
 * arrive. The reader's own does not: the upload path drops that one address out of the caches
 * itself, which is what [io.github.plaza.designsys.image.evictImage] is for.
 *
 * Only writes are touched. Freshness, revalidation, what a 304 does to a stored entry — all of that
 * stays [delegate]'s, computed from the headers it finds, one of which this wrote.
 */
class LongLivedImageCacheStrategy(
    private val delegate: CacheStrategy,
    /** True for an address whose content may change under it — see the class note. */
    private val isLongLived: (url: String) -> Boolean,
    private val maxAge: Duration = DEFAULT_MAX_AGE,
) : CacheStrategy {
    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult = delegate.read(cacheResponse, networkRequest, options)

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult {
        val result = delegate.write(cacheResponse, networkRequest, networkResponse, options)
        if (!isLongLived(networkRequest.url)) return result
        // Null when the delegate decided this response is not to be stored at all. Stamping a
        // lifetime on something nobody is keeping would be inventing a cache entry, not extending one.
        val stored = result.response ?: return result
        return CacheStrategy.WriteResult(stored.copy(headers = stored.headers.withMaxAge(maxAge)))
    }

    private companion object {
        /**
         * A week. An avatar changes rarely enough that a reader is unlikely to notice, and often
         * enough that never asking again — which is what the default strategy does — is wrong.
         */
        val DEFAULT_MAX_AGE = 7.days
    }
}

/**
 * `set`, not `add`: the point is to replace whatever the server sent, and two `Cache-Control` lines
 * would leave which one wins up to whoever parses them.
 */
private fun NetworkHeaders.withMaxAge(maxAge: Duration): NetworkHeaders =
    newBuilder()
        .set("Cache-Control", "public, max-age=${maxAge.inWholeSeconds}")
        .build()
