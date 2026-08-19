package io.github.plaza.core.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Lets OkHttp read and write the sign-in browser's cookie store.
 *
 * Login and Cloudflare challenges can only be completed in a real WebView, and everything else in
 * the app is a plain OkHttp request. Rather than copying cookies back and forth, both sides go
 * through one [SessionCookieStore] — [WebViewCookieStore], which is Android's own `CookieManager`
 * and is what makes the name of this class still true.
 *
 * All of it is translation: `Set-Cookie` strings in one direction, OkHttp `Cookie` values in the
 * other. Reading a *session* out of the same store is [SessionCookies], which needs no OkHttp and
 * no device and therefore lives in `commonMain`.
 */
class WebViewCookieJar(
    private val store: SessionCookieStore,
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = store.cookieHeader(url.toString()) ?: return emptyList()
        return header.split(';')
            .mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val target = url.toString()
        cookies.forEach { store.setCookie(target, it.toString()) }
        store.flush()
    }
}
