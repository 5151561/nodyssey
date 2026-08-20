package io.github.plaza.core.net

/**
 * The cookie view the HTTP client and the sign-in browser share.
 *
 * A *view*, not a store, and the distinction is the whole reason this is an interface rather than
 * the platform's own type. On Android there is genuinely one store behind it — `CookieManager` is
 * both what the WebView writes and what OkHttp reads — so the implementation is a direct handle on
 * it. On Apple there are two jars: WebKit writes to `WKWebsiteDataStore.httpCookieStore` and
 * `NSURLSession` reads `NSHTTPCookieStorage`, and nothing syncs them on its own. The
 * implementation there is therefore the `NSHTTPCookieStorage` side, kept in step with WebKit by the
 * bridge step D3 owns — see `NSUrlSessionTransport`. A test implements this with a map.
 *
 * What is *not* about the platform, in any of those three, is reading a signed-in state off the
 * names a site issues; that lives in [SessionCookies] and needs no device.
 *
 * The interface speaks in raw header strings, and is synchronous. Both are affordable only because
 * every implementation is something that can answer immediately: `WKHTTPCookieStore.getAllCookies`
 * answers on a callback and so must never sit directly behind [cookieHeader] — the mirror is what
 * makes this signature honest, not an implementation detail of it.
 */
interface SessionCookieStore {
    /**
     * The `Cookie` header this store would send to [url] — `a=1; b=2` — or null when it has none.
     *
     * Attributes are not included: a store returns what it would *send*, which is names and values.
     */
    fun cookieHeader(url: String): String?

    /** [cookie] is one `Set-Cookie` value, attributes and all; the store decides what it keeps. */
    fun setCookie(url: String, cookie: String)

    fun removeAll()

    /**
     * Writes whatever is held in memory to wherever the store persists.
     *
     * Named here because it is not an implementation detail: the WebView batches its writes, and the
     * cookie worth never losing — the session the site issues at login — arrives from an XHR rather
     * than a page load, so there is no navigation to hang the write on.
     */
    fun flush()
}
