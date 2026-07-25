package io.github.nsreader.core.net

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Shares one cookie store between OkHttp and the WebView.
 *
 * Login and Cloudflare challenges can only be completed in a real WebView, and everything else in
 * the app is a plain OkHttp request. Rather than copying cookies back and forth, both sides read
 * and write Android's [CookieManager], which also persists them across launches for free.
 */
class WebViewCookieJar(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) : CookieJar {

    init {
        cookieManager.setAcceptCookie(true)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return header.split(';')
            .mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val target = url.toString()
        cookies.forEach { cookieManager.setCookie(target, it.toString()) }
        cookieManager.flush()
    }

    /** True once the session cookie NodeSeek issues at login is present. */
    fun isLoggedIn(): Boolean {
        val header = cookieManager.getCookie(io.github.nsreader.core.NodeSeekSite.BASE_URL).orEmpty()
        return SESSION_COOKIE_NAMES.any { header.contains("$it=") }
    }

    fun clearSession() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    private companion object {
        /** NodeSeek sets `session`; the JWT-style `token` shows up on some deployments. */
        val SESSION_COOKIE_NAMES = listOf("session", "token")
    }
}
