package io.github.plaza.core.net

/**
 * The one cookie store OkHttp and the sign-in browser both use.
 *
 * An interface rather than the platform's own type because *which* store it is — Android's
 * `CookieManager`, `WKHTTPCookieStore`, a map in a test — is the only part of sharing cookies that
 * is about the platform. Everything else, and in particular reading a signed-in state off the names
 * a site issues, is about the site; that lives in [WebViewCookieJar] and now needs no device.
 *
 * The whole interface speaks in raw header strings for the same reason: `Set-Cookie` is what every
 * one of those stores already takes and returns, so nothing has to be parsed to cross this line.
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
