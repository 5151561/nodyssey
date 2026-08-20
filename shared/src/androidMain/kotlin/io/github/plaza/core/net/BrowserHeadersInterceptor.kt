package io.github.plaza.core.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Makes an OkHttp request look like the browser the site expects, because the site is checking.
 *
 * Both halves of this app talk to Cloudflare-fronted hosts, and Cloudflare scores a request on how
 * ordinary it looks. OkHttp's idea of ordinary is not a browser's: it sends `okhttp/4.x` as its
 * agent unless told otherwise, and it never sends `Accept-Language` at all. Each omission has cost
 * this app a visible failure — the second one is why post-879848's attachment failed for everyone
 * on an image host that served the same picture to every browser on the same connection. See
 * [resolveUserAgent] and [deviceAcceptLanguage] for where each value comes from and why it has to
 * be the device's own rather than something plausible-looking.
 *
 * Every header is set only when the caller did not: a request that already carries one means it,
 * and this exists to fill silence rather than to overrule.
 *
 * [referer] is nullable and deliberately unset for third-party hosts: the forum's referrer tells an
 * image host or an API about a browsing session it is not part of. On the forum's own client it is
 * the opposite — some image hosts serve only requests referred by the forum — which is why the
 * decision belongs to the caller. See [CrossOriginRefererInterceptor] for the redirect half of it.
 */
class BrowserHeadersInterceptor(
    private val userAgent: String,
    private val acceptLanguage: String,
    private val referer: String? = null,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", userAgent)
        }
        if (request.header("Accept-Language") == null) {
            builder.header("Accept-Language", acceptLanguage)
        }
        if (referer != null && request.header("Referer") == null) {
            builder.header("Referer", referer)
        }
        return chain.proceed(builder.build())
    }
}
