package io.github.plaza.core.net

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Drops `Referer` once a redirect has carried a request to another host.
 *
 * A browser decides per response whether the referrer survives a redirect, and the hosts that
 * matter here decide it must not. A post image served through a redirecting image host is the case
 * that found this: `pic1.imgdb.cn` answers with `Referrer-Policy: no-referrer` and a 302 to Baidu's
 * `wkphoto.cdn.bcebos.com`, so the browser arrives at the CDN with no referrer and is served the
 * picture, while OkHttp — which follows a redirect with the original headers intact, dropping only
 * `Authorization` — arrives still carrying `Referer: nodeseek.com` and is refused with a 403. Every
 * image behind that host failed in the app and none of them failed on the web.
 *
 * The first hop keeps its `Referer`, which is both what a browser sends and what a host with the
 * opposite rule needs — hotlink protection that serves only requests referred by the forum. Only
 * the hops a redirect adds can lose it.
 *
 * Host, not origin: an `http` → `https` upgrade of the same host is not the situation this is
 * about, and hotlink protection is keyed on the host anyway.
 *
 * Register with `addNetworkInterceptor`. An application interceptor runs once, before there is a
 * redirect to know about, and would never see the hop this exists for.
 */
class CrossOriginRefererInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(withoutForeignReferer(chain.call().request().url, chain.request()))
}

/**
 * [hop] without its `Referer` if it has left the host [addressed] named.
 *
 * [addressed] is the URL the caller asked for; [hop] is the request actually about to go out, which
 * is the same one until a redirect moves it.
 */
internal fun withoutForeignReferer(
    addressed: HttpUrl,
    hop: Request,
): Request =
    if (hop.header("Referer") == null || hop.url.host == addressed.host) {
        hop
    } else {
        hop.newBuilder().removeHeader("Referer").build()
    }
