package io.github.nodyssey.image

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Coil's call factory: the forum's client for the forum's own images, the cookie-less one for
 * everything a post embeds from elsewhere.
 *
 * The split exists because the two kinds of image need opposite things from a cookie jar. An avatar
 * on nodeseek.com sits behind the same Cloudflare wall as the pages and fails without the session's
 * `cf_clearance`; a picture on a third-party host needs no cookie of ours — and a jar that *saves*
 * what such a host sets back is a persistent tracking identifier planted through an `<img>` tag.
 * See `imageContentClient` in `DefaultAppContainer` for the full argument.
 *
 * The clients arrive as suppliers rather than instances so building this router does not force the
 * container's lazy graph; each is asked for on the first call that needs it, which is how the
 * previous single-client lambda behaved too.
 */
class ImageCallRouter(
    private val forum: () -> OkHttpClient,
    private val elsewhere: () -> OkHttpClient,
) : Call.Factory {
    override fun newCall(request: Request): Call {
        val client = if (isForumImageHost(request.url.host)) forum() else elsewhere()
        return client.newCall(request)
    }
}

/**
 * Whether [host] is NodeSeek's own — the bare domain or anything under it.
 *
 * Subdomains are included on the cookie's own terms: a cookie set for `.nodeseek.com` is one the
 * site expects back on every host under it, so the routing has to draw the line where the cookie
 * scope does or an image on a forum subdomain would arrive signed out.
 */
internal fun isForumImageHost(host: String): Boolean {
    val lower = host.lowercase()
    return lower == "nodeseek.com" || lower.endsWith(".nodeseek.com")
}
