package io.github.plaza.core.net

import android.webkit.CookieManager

/**
 * [SessionCookieStore] on Android's own `CookieManager`.
 *
 * The reason this is the app's cookie store and not an OkHttp jar of its own: login and Cloudflare
 * challenges can only be completed in a real WebView, so the WebView's store is where those cookies
 * land. Sharing it rather than copying out of it means there is no moment where one side has a
 * cookie the other does not — and it persists across launches for free.
 */
class WebViewCookieStore(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) : SessionCookieStore {

    init {
        cookieManager.setAcceptCookie(true)
    }

    override fun cookieHeader(url: String): String? = cookieManager.getCookie(url)

    override fun setCookie(url: String, cookie: String) = cookieManager.setCookie(url, cookie)

    override fun removeAll() = cookieManager.removeAllCookies(null)

    override fun flush() = cookieManager.flush()
}
