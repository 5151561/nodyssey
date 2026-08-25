package io.github.plaza.core.net

import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL

/**
 * [SessionCookieStore] on the cookie jar `NSURLSession` reads — **not** on the one a `WKWebView`
 * writes to.
 *
 * On Android a single store — `CookieManager` — is both what the sign-in WebView writes to and what
 * OkHttp reads, so `WebViewCookieJar` is pure translation and nothing has to be copied. WebKit has no
 * such store: a `WKWebView` writes to its `WKWebsiteDataStore.httpCookieStore`, which is a different
 * jar from [NSHTTPCookieStorage.sharedHTTPCookieStorage] that `NSURLSession` reads, and its API is
 * asynchronous besides. [WebKitCookieBridge] is what carries one to the other.
 *
 * The storage is a required argument rather than a defaulted one so that a caller has to say which
 * jar it means. It is also why the interface's synchronous [cookieHeader] is honest here and would
 * not be if this read WebKit directly: `WKHTTPCookieStore.getAllCookies` answers on a callback, so
 * the bridge has to keep a mirror this can read rather than reaching across at call time.
 */
class AppleCookieStore(
    private val storage: NSHTTPCookieStorage,
) : SessionCookieStore {

    override fun cookieHeader(url: String): String? {
        val target = NSURL.URLWithString(url) ?: return null
        val cookies = storage.cookiesForURL(target).orEmpty()
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { cookie ->
            val typed = cookie as NSHTTPCookie
            "${typed.name}=${typed.value}"
        }
    }

    override fun setCookie(url: String, cookie: String) {
        val target = NSURL.URLWithString(url) ?: return
        val parsed =
            NSHTTPCookie.cookiesWithResponseHeaderFields(
                mapOf<Any?, Any?>("Set-Cookie" to cookie),
                forURL = target,
            )
        storage.setCookies(parsed, forURL = target, mainDocumentURL = null)
    }

    // Suspend only because the Android implementation genuinely is; deletion here is synchronous.
    override suspend fun removeAll() {
        storage.cookies.orEmpty().forEach { storage.deleteCookie(it as NSHTTPCookie) }
    }

    // `NSHTTPCookieStorage` writes through on its own; there is no batched state to push. Android's
    // `CookieManager` is the one that needs telling, which is why the method exists at all.
    override fun flush() = Unit
}
