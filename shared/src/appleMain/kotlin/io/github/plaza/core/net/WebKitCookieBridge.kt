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
 * **Deletions are mirrored, and only for cookies WebKit is known to own and still holds unchanged.**
 * A signed-out site clears its cookie and the app has to notice, so a cookie that was in WebKit and no
 * longer is gets deleted from the storage too. What was there is tracked rather than assumed, because
 * the two jars are not the same shape: `NSURLSession` writes cookies into the storage that WebKit
 * never saw, and deleting those on the grounds that WebKit does not have them would throw away the
 * session a plain API call just issued.
 *
 * Ownership is narrower than "WebKit has it", in two directions, and both are the same rule read
 * twice — *nothing the app put there is WebKit's until WebKit writes over it*:
 *
 * - What [seed] injected does not become WebKit's by virtue of being handed to it. Everything the
 *   storage holds goes in, so claiming it all back would hand WebKit ownership of the session a plain
 *   API call issued — the exact cookie the paragraph above refuses to delete — one step later and
 *   through the front door. It becomes WebKit's the moment WebKit writes a different value under it,
 *   which is what a sign-in does and what a sign-out is preceded by.
 * - The value is tracked with the key, because a session the app refreshed over a plain API call
 *   carries the same name, domain and path as the one WebKit is about to drop, and a diff on the key
 *   alone deletes the new cookie along with the old.
 *
 * Both err the same way: a delete that should have been mirrored and was not costs one stale cookie,
 * which the next request answers with a 401 the app already handles. The other direction signs the
 * user out of an app that was never signed out.
 */
class WebKitCookieBridge(
    private val cookieStore: WKHTTPCookieStore,
    private val storage: NSHTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage,
) {
    /**
     * The cookies last seen in WebKit *and* WebKit's to lose: name-domain-path to the value that was
     * under it.
     *
     * What the deletion diff runs against, so a cookie that disappeared from WebKit can be told apart
     * both from one that was never there and from one that is still there under a newer value.
     */
    private var webKitOwned: Map<String, String> = emptyMap()

    /**
     * What [seed] handed to WebKit and WebKit has not written over yet, by the same key.
     *
     * Held out of [webKitOwned] for as long as the value matches. An entry leaves the moment WebKit
     * reports something else under that key — from there it is WebKit's, and its disappearance is a
     * sign-out worth mirroring.
     */
    private var seeded: Map<String, String> = emptyMap()

    /**
     * One pass at a time, with at most one more queued behind it.
     *
     * `getAllCookies` answers on a callback, so two changes in quick succession put two passes in
     * flight over the same [webKitOwned]: they diff against the same stale snapshot, and the one that
     * lands second re-`setCookie`s what the first just deleted — a sign-out undone. Every line that
     * touches this state runs on the main queue, the observer because that is where WebKit calls it and
     * [drain] because it hops there, so a flag is enough and a lock is not.
     */
    private var copying = false
    private var recopyQueued = false
    private val awaiting = mutableListOf<() -> Unit>()

    private val observer = ChangeObserver { copyOut() }

    /**
     * Puts the app's cookies into WebKit and takes note of what it put there.
     *
     * Awaited rather than fired off: the caller's next act is to load a URL, and a page that starts
     * before its cookies arrive is a page loaded as a stranger.
     */
    suspend fun seed() = withContext(Dispatchers.Main) {
        val mine = storage.cookies.orEmpty().filterIsInstance<NSHTTPCookie>()
        // Recorded before the first copy back out, which is what reads it.
        seeded = mine.associate { it.key() to it.value }
        mine.forEach { cookie ->
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

    /**
     * Asks for a pass, and for one after it if a pass is already running.
     *
     * [onDone] fires after a pass that began no earlier than this call, which is what lets [drain]
     * promise that what WebKit holds *now* is in the storage rather than that some recent version of
     * it is.
     */
    private fun copyOut(onDone: (() -> Unit)? = null) {
        if (onDone != null) awaiting += onDone
        if (copying) {
            recopyQueued = true
            return
        }
        copying = true
        runCopy()
    }

    private fun runCopy() {
        cookieStore.getAllCookies { cookies ->
            val live = cookies.orEmpty().filterIsInstance<NSHTTPCookie>()
            val liveByKey = live.associate { it.key() to it.value }

            // Gone from WebKit, and WebKit had it under this very value: the site cleared it, so the
            // mirror clears it too. A value that no longer matches means something outside WebKit wrote
            // it in the meantime — a plain API call issuing a fresh session is exactly that — and the
            // cookie sitting there now is not the one that vanished.
            val vanished = webKitOwned.filterKeys { it !in liveByKey }
            if (vanished.isNotEmpty()) {
                storage.cookies.orEmpty()
                    .filterIsInstance<NSHTTPCookie>()
                    .filter { vanished[it.key()] == it.value }
                    .forEach { storage.deleteCookie(it) }
            }

            // `setCookie` replaces one with the same name, domain and path, so this is an update as
            // much as an insert.
            live.forEach { storage.setCookie(it) }

            // What `seed` handed over stays the app's until WebKit writes a different value under it;
            // only then does its later disappearance mean the site cleared something.
            seeded = seeded.filter { (key, value) -> liveByKey[key] == value }
            webKitOwned = liveByKey.filterKeys { it !in seeded }

            if (recopyQueued) {
                recopyQueued = false
                runCopy()
            } else {
                copying = false
                val done = awaiting.toList()
                awaiting.clear()
                done.forEach { it() }
            }
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
