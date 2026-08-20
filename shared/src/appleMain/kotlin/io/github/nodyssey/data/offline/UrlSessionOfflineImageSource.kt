package io.github.nodyssey.data.offline

import io.github.plaza.core.net.getBytes
import io.github.plaza.core.toByteArray
import platform.Foundation.NSURLSession

/**
 * The app's own `NSURLSession`, so a stored picture arrives under the same cookies, `User-Agent` and
 * `Accept-Language` as one the reader is looking at — see `OkHttpOfflineImageSource` in `androidMain`
 * for the failure that argument is drawn from.
 *
 * The size limit is handed down to [getBytes] rather than applied to what comes back, and that is the
 * whole point of it: a background download must not discover a 40 MB screenshot by first holding all
 * 40 MB. The declared length is refused at the response header and an undeclared body is stopped
 * mid-stream — see `BoundedBodyDelegate`.
 *
 * No dispatcher, where the Android side takes one: `NSURLSession` is callback-driven and occupies no
 * thread while a request is in flight, which is the same fact `ioDispatcher()` in `Platform.apple.kt`
 * turns on. The copy out of `NSData` is the only work this does, and it happens on whichever
 * dispatcher the caller was already on.
 */
class UrlSessionOfflineImageSource(
    private val session: () -> NSURLSession,
) : OfflineImageSource {
    override suspend fun fetch(
        url: String,
        maxBytes: Long,
    ): ByteArray? {
        val response = session().getBytes(url, maxBytes = maxBytes) ?: return null
        return response.data.toByteArray()
    }
}
