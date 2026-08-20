package io.github.plaza.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import kotlin.coroutines.resume

/**
 * One GET, read as bytes rather than as text.
 *
 * [HttpTransport] is deliberately the only network contract above this layer, and it hands back a
 * decoded `String` because everything above it reads HTML or JSON. A picture is the one thing that is
 * neither, and this is the Apple side of that exception — the Android side is `OkHttpOfflineImageSource`
 * reaching for its `OkHttpClient` directly, for exactly the same reason.
 *
 * The session is the app's own, so a picture arrives under the same cookies, `User-Agent` and
 * `Accept-Language` as the page it was embedded in. That is not a nicety: the attachment host is
 * behind Cloudflare and answers a request missing those with a challenge page.
 *
 * @return null for any answer that is not a 2xx with a body.
 */
suspend fun NSURLSession.getBytes(url: String): UrlSessionBytes? {
    val target = NSURL.URLWithString(url) ?: return null
    return suspendCancellableCoroutine { continuation ->
        val task =
            dataTaskWithURL(target) { data: NSData?, response, _ ->
                val http = response as? NSHTTPURLResponse
                val code = http?.statusCode?.toInt()
                continuation.resume(
                    if (code == null || code !in 200..299 || data == null) {
                        null
                    } else {
                        UrlSessionBytes(
                            data = data,
                            // Absent, or -1 when the server did not say — which is the case this
                            // exists to distinguish, since a caller with a size limit has to know
                            // whether it is being told anything at all.
                            declaredLength = http.expectedContentLength.takeIf { it >= 0 },
                            mimeType = http.MIMEType,
                            headers = http.allHeaderFields.entries.mapNotNull { entry ->
                                val name = entry.key as? String ?: return@mapNotNull null
                                val value = entry.value as? String ?: return@mapNotNull null
                                name to value
                            },
                        )
                    },
                )
            }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

/**
 * What [getBytes] read.
 *
 * [headers] is carried out whole rather than reduced to the two fields above it, because the caller
 * that needs them is a cache: `Cache-Control`, `ETag` and `Last-Modified` are the difference between
 * an image store that revalidates and one that holds its first answer forever. The first version of
 * this shell dropped them, and the way that showed up was a picture the server had since replaced —
 * a hotlink-protection notice — still being drawn after the header that caused it was fixed.
 */
class UrlSessionBytes(
    val data: NSData,
    val declaredLength: Long?,
    val mimeType: String?,
    val headers: List<Pair<String, String>> = emptyList(),
)
