package io.github.plaza.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKHTTPCookieStoreObserverProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Keeps `WKHTTPCookieStore` and [NSHTTPCookieStorage] saying the same thing, for as long as a
 * `WKWebView` is on screen.
 *
 * This is the piece Android does not need. There, the sign-in browser and the HTTP client read one
 * `CookieManager`, so [SessionCookieStore] is pure translation and a session that arrives in the
 * browser is already the session the next request sends. WebKit has its own jar, `NSURLSession` reads
 * another, and nothing moves a cookie between them — which is the whole reason
 * [NSUrlSessionTransport] shipped with only one side of this.
 *
 * Three things happen, in this order:
 *
 * 1. [seed] copies what the app already holds *into* WebKit, so the browser opens as the same visitor
 *    the app is — a Cloudflare challenge answered against a blank jar would be answered for nobody.
 * 2. [start] registers a `WKHTTPCookieStoreObserver`, and every change WebKit reports is copied back
 *    out. This is what makes [AppleCookieStore.cookieHeader] honest while a page is live: the screen
 *    polls the session twice a second and reads a mirror, never WebKit itself, whose own accessor
 *    answers on a callback.
 * 3. [drain] copies once more and waits for it, for the change that lands as the screen is closing.
 *    NodeSeek issues its session over an XHR, so there is no navigation to hang that last copy on.
 *
 * **Deletions are mirrored, and only for cookies WebKit is known to own.** A signed-out site clears
 * its cookie and the app has to notice, so a cookie that was in WebKit and no longer is gets deleted
 * from the storage too. The set is tracked rather than assumed because the two jars are not the same
 * shape: `NSURLSession` writes cookies into the storage that WebKit never saw, and deleting those on
 * the grounds that WebKit does not have them would throw away the session a plain API call just
 * issued.
 */
class WebKitCookieBridge(
    private val cookieStore: WKHTTPCookieStore,
    private val storage: NSHTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage,
) {
    /**
     * The cookies last seen in WebKit, by name-domain-path.
     *
     * What [drain] and the observer diff against, so a cookie that disappears from WebKit can be told
     * apart from one that was never there.
     */
    private var webKitOwned: Set<String> = emptySet()

    private val observer = ChangeObserver { copyOut(onDone = {}) }

    /**
     * Puts the app's cookies into WebKit and takes note of what WebKit then holds.
     *
     * Awaited rather than fired off: the caller's next act is to load a URL, and a page that starts
     * before its cookies arrive is a page loaded as a stranger.
     */
    suspend fun seed() = withContext(Dispatchers.Main) {
        storage.cookies.orEmpty().filterIsInstance<NSHTTPCookie>().forEach { cookie ->
            suspendCancellableCoroutine { continuation ->
                cookieStore.setCookie(cookie) { continuation.resume(Unit) }
            }
        }
        drain()
    }

    /** Starts mirroring. Every WebKit change from here on is copied to the storage. */
    fun start() {
        cookieStore.addObserver(observer)
    }

    /** Stops mirroring. Safe to call without [start]; removing an unregistered observer is a no-op. */
    fun stop() {
        cookieStore.removeObserver(observer)
    }

    /** One copy out of WebKit, awaited — the last one, taken as the screen goes away. */
    suspend fun drain() = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            copyOut { continuation.resume(Unit) }
        }
    }

    private fun copyOut(onDone: () -> Unit) {
        cookieStore.getAllCookies { cookies ->
            val live = cookies.orEmpty().filterIsInstance<NSHTTPCookie>()
            val liveKeys = live.mapTo(mutableSetOf()) { it.key() }

            // Gone from WebKit, and WebKit had it: the site cleared it, so the mirror clears it too.
            val vanished = webKitOwned - liveKeys
            if (vanished.isNotEmpty()) {
                storage.cookies.orEmpty()
                    .filterIsInstance<NSHTTPCookie>()
                    .filter { it.key() in vanished }
                    .forEach { storage.deleteCookie(it) }
            }

            // `setCookie` replaces one with the same name, domain and path, so this is an update as
            // much as an insert.
            live.forEach { storage.setCookie(it) }
            webKitOwned = liveKeys
            onDone()
        }
    }

    /**
     * `WKHTTPCookieStoreObserver` as a Kotlin object.
     *
     * Its own class rather than the bridge conforming to the protocol: an observer is retained by the
     * store it is added to, and keeping that a separate object is what stops the bridge from outliving
     * the screen that owns it by virtue of being observed.
     */
    private class ChangeObserver(
        private val onChanged: () -> Unit,
    ) : NSObject(),
        WKHTTPCookieStoreObserverProtocol {
        override fun cookiesDidChangeInCookieStore(cookieStore: WKHTTPCookieStore) {
            onChanged()
        }
    }
}

/** Name, domain and path — what makes two `Set-Cookie`s the same cookie rather than a second one. */
private fun NSHTTPCookie.key(): String = "$name\n$domain\n$path"
