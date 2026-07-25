package io.github.nsreader.core.net

import android.webkit.CookieManager
import io.github.nsreader.core.NodeSeekSite
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

    /**
     * Writes the in-memory cookie store to disk.
     *
     * The WebView batches its writes, and the one cookie worth never losing — the session NodeSeek
     * issues at login — arrives from an XHR rather than a page load, so there is no navigation to
     * hang the flush on.
     */
    fun flush() {
        cookieManager.flush()
    }

    /**
     * What the store says about our access, reduced to what the app needs.
     *
     * Values never leave this class: callers get two booleans and a fingerprint, which is enough to
     * notice a change and not enough to leak a session.
     *
     * The two booleans match cookies by name; the fingerprint deliberately does not. Naming is the
     * one thing here we are guessing at — [SESSION_COOKIE_NAMES] is what NodeSeek is believed to set,
     * not something the app can prove — so the signal that *reloads content* is kept independent of
     * the guess. Get the name wrong and a label is wrong; the feed still refreshes.
     */
    fun snapshot(): CookieSnapshot {
        val pairs = cookiePairs()
        return CookieSnapshot(
            // A present-but-empty cookie is how a sign-out looks on the wire, so the value has to be
            // checked. Exact name matching for the same reason `contains("session=")` was wrong: it
            // also matched a cookie called `xsession`.
            isSignedIn = pairs.any { (name, value) -> name in SESSION_COOKIE_NAMES && value.isNotBlank() },
            hasClearance = pairs.any { (name, value) -> name == CLEARANCE_COOKIE && value.isNotBlank() },
            fingerprint =
            pairs
                .filterNot { (name, _) -> isCloudflareNoise(name) }
                .sortedBy { (name, _) -> name }
                .joinToString(";") { (name, value) -> "$name=$value" }
                .hashCode(),
        )
    }

    /** Cookie *names* only — used for diagnostics; values are never logged. */
    fun cookieNames(): List<String> = cookiePairs().map { (name, _) -> name }

    fun clearSession() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    /**
     * True for the cookies Cloudflare manages on its own schedule: the bot-management cookie, the
     * per-visitor id, and the challenge intermediates that churn on every tick of a live challenge.
     *
     * That last group is why this is a prefix test rather than a list. A fingerprint that moved with
     * them would fire a cache invalidation and a feed reload on every tick of a challenge the user is
     * still solving — requests aimed at Cloudflare at the worst possible moment.
     *
     * [CLEARANCE_COOKIE] is the deliberate exception: it shares the prefix but it *is* the outcome.
     */
    private fun isCloudflareNoise(name: String): Boolean =
        name != CLEARANCE_COOKIE &&
            (name.startsWith("__cf") || name.startsWith("_cf") || name.startsWith("cf_"))

    private fun cookiePairs(): List<Pair<String, String>> =
        cookieManager
            .getCookie(NodeSeekSite.BASE_URL)
            .orEmpty()
            .split(';')
            .mapNotNull { raw ->
                val entry = raw.trim()
                if (entry.isEmpty()) return@mapNotNull null
                val name = entry.substringBefore('=').trim()
                if (name.isEmpty()) null else name to entry.substringAfter('=', "").trim()
            }

    private companion object {
        /** NodeSeek sets `session`; the JWT-style `token` shows up on some deployments. */
        val SESSION_COOKIE_NAMES = listOf("session", "token")

        /** Cloudflare's proof that a human cleared the challenge. Bound to the UA, hence the shared one. */
        const val CLEARANCE_COOKIE = "cf_clearance"
    }
}

/** What the shared cookie store says about our access to NodeSeek, without the secrets. */
data class CookieSnapshot(
    val isSignedIn: Boolean,
    val hasClearance: Boolean,
    /** Changes whenever a deciding cookie is issued, renewed or cleared. */
    val fingerprint: Int,
)
