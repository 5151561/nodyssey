package io.github.nodyssey.data.offline

import io.github.plaza.core.net.getBytes
import io.github.plaza.core.toByteArray
import platform.Foundation.NSURLSession

/**
 * The app's own `NSURLSession`, so a stored picture arrives under the same cookies, `User-Agent` and
 * `Accept-Language` as one the reader is looking at — see `OkHttpOfflineImageSource` in `androidMain`
 * for the failure that argument is drawn from.
 *
 * The size limit is checked against the declared content length before the body is read where the
 * server offers one, and against the body afterwards where it does not: a background download must
 * not discover a 40 MB screenshot by holding it in memory.
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
        val response = session().getBytes(url) ?: return null
        if (response.declaredLength != null && response.declaredLength > maxBytes) return null
        val bytes = response.data.toByteArray()
        return if (bytes.size > maxBytes) null else bytes
    }
}
