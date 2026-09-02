package io.github.plaza.core.net

/**
 * Reads a session out of the one cookie store the HTTP client and the sign-in browser share.
 *
 * Nothing here knows which store that is — [WebViewCookieStore] on Android's `CookieManager`, an
 * `NSHTTPCookieStorage` on Apple, a map in a test. What it does know is the site: which cookie
 * names mean signed in, which ones Cloudflare churns on its own schedule, and what a change in them
 * should cost. That is the half worth keeping off any one platform, and it is the whole of this
 * file.
 *
 * The class this was carved out of was `WebViewCookieJar`, which is an OkHttp `CookieJar` and
 * therefore could not come along; that name now belongs to the thin adapter beside the transport.
 */
class SessionCookies(
    private val config: SiteConfig,
    private val store: SessionCookieStore,
) {
    /**
     * What the store says about our access, reduced to what the app needs.
     *
     * Values never leave this class: callers get two booleans and a fingerprint, which is enough to
     * notice a change and not enough to leak a session.
     *
     * The two booleans match cookies by name; the fingerprint deliberately does not. Naming is the
     * one thing here we are guessing at — [SiteConfig.sessionCookieNames] is what a site is
     * *believed* to set, not something the app can prove — so the signal that *reloads content* is
     * kept independent of the guess. Get the name wrong and a label is wrong; the feed still
     * refreshes.
     */
    fun snapshot(): CookieSnapshot {
        val pairs = cookiePairs()
        return CookieSnapshot(
            // A present-but-empty cookie is how a sign-out looks on the wire, so the value has to be
            // checked. Exact name matching for the same reason `contains("session=")` was wrong: it
            // also matched a cookie called `xsession`.
            isSignedIn = pairs.any { (name, value) -> name in config.sessionCookieNames && value.isNotBlank() },
            hasClearance = pairs.any { (name, value) -> name == CLEARANCE_COOKIE && value.isNotBlank() },
            fingerprint =
            pairs
                .filterNot { (name, _) -> isCloudflareNoise(name) || isOurs(name) }
                .sortedBy { (name, _) -> name }
                .joinToString(";") { (name, value) -> "$name=$value" }
                .hashCode(),
        )
    }

    /** Cookie *names* only — used for diagnostics; values are never logged. */
    fun cookieNames(): List<String> = cookiePairs().map { (name, _) -> name }

    /** See [SessionCookieStore.flush] for why this is worth calling by hand. */
    fun flush() {
        store.flush()
    }

    /**
     * Writes the site's own light/dark cookie, so that a request from this app looks like one from a
     * reader who has been on the site before.
     *
     * Here rather than beside the theme because this is the class that holds the one cookie jar, and
     * a jar is what this has to be written into: it is not a header a client can add — the WebView
     * and the HTTP client have to agree on it, and on Apple that agreement is a mirror rather than a
     * shared store. [SiteConfig.colorSchemeCookie] is why a colour is being written at all, and it is
     * the endpoint quirk documented there, not the colour, that makes this load-bearing.
     *
     * Flushed on the caller's behalf: on Android the store batches its writes, and the request that
     * needs this cookie can be the next line of a cold start.
     */
    fun applyColorScheme(dark: Boolean) {
        val cookie = config.colorSchemeCookie ?: return
        val value = if (dark) cookie.dark else cookie.light
        // Attributes the site's own switch writes too. `Max-Age` rather than a session cookie so the
        // next cold start does not have to reach this line before its first request does.
        store.setCookie(config.baseUrl, "${cookie.name}=$value; Path=/; Max-Age=$COLOR_SCHEME_MAX_AGE")
        store.flush()
    }

    suspend fun clearSession() {
        store.removeAll()
        store.flush()
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
        store
            .cookieHeader(config.baseUrl)
            .orEmpty()
            .split(';')
            .mapNotNull { raw ->
                val entry = raw.trim()
                if (entry.isEmpty()) return@mapNotNull null
                val name = entry.substringBefore('=').trim()
                if (name.isEmpty()) null else name to entry.substringAfter('=', "").trim()
            }

    /**
     * True for a cookie this app wrote itself.
     *
     * Kept out of the fingerprint for the same reason as Cloudflare's noise, arrived at from the
     * other end: the fingerprint is what the feed reloads on, and a reader switching the app to dark
     * mode has learned nothing new about the site.
     */
    private fun isOurs(name: String): Boolean = name == config.colorSchemeCookie?.name

    private companion object {
        /** Cloudflare's proof that a human cleared the challenge. Bound to the UA, hence the shared one. */
        const val CLEARANCE_COOKIE = "cf_clearance"

        /** A year, which is what the site's own switch writes. */
        const val COLOR_SCHEME_MAX_AGE = 31_536_000
    }
}

/** What the shared cookie store says about our access to the site, without the secrets. */
data class CookieSnapshot(
    val isSignedIn: Boolean,
    val hasClearance: Boolean,
    /** Changes whenever a deciding cookie is issued, renewed or cleared. */
    val fingerprint: Int,
)
