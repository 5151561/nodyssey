package io.github.plaza.core.net

import android.webkit.CookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

    // `removeAllCookies` completes on a callback; passing null used to mean "and never find out
    // when". Sign-out re-reads the store the moment this returns, so returning before the callback
    // let it read the cookies it had just removed and conclude nothing changed.
    override suspend fun removeAll(): Unit =
        suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies { continuation.resume(Unit) }
        }

    override fun flush() = cookieManager.flush()
}
