package io.github.nodyssey.data.offline

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches one picture's bytes for storage. Null when it could not be had, or is not worth keeping. */
interface OfflineImageSource {
    suspend fun fetch(
        url: String,
        maxBytes: Long,
    ): ByteArray?
}

/**
 * The app's own OkHttp client, so a stored picture arrives under the same cookies, `User-Agent`,
 * `Accept-Language` and `Referer` as one the reader is looking at.
 *
 * That is not a nicety: the attachment host is behind Cloudflare and answers a request missing
 * those headers with a challenge page, which is how the app once failed to load an image every
 * browser on the same connection could see.
 *
 * The size limit is checked against `Content-Length` before the body is read where the server
 * offers one, and against the body afterwards where it does not — a background worker must not
 * discover a 40 MB screenshot by holding it in memory.
 */
class OkHttpOfflineImageSource(
    private val client: () -> OkHttpClient,
    private val dispatchers: AppDispatchers,
) : OfflineImageSource {
    override suspend fun fetch(
        url: String,
        maxBytes: Long,
    ): ByteArray? =
        withContext(dispatchers.io) {
            val request = Request.Builder().url(url).build()
            client().newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful) return@withContext null
                if (body.contentLength() > maxBytes) return@withContext null
                val bytes = body.bytes()
                if (bytes.size > maxBytes) null else bytes
            }
        }
}
