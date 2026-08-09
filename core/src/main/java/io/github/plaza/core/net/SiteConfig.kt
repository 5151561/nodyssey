package io.github.plaza.core.net

/**
 * Everything this module has to be told about the forum it is talking to.
 *
 * The networking here — the cookie bridge, the User-Agent, the "is this a real page or an
 * interstitial" test — is the same work for any scraped forum. What differs is a handful of strings,
 * and those used to be constants one site's own object handed out to these classes directly, which
 * is what made the whole layer unusable for a second site.
 *
 * One object rather than a parameter per class so that adding a site means writing one value, and so
 * that a site whose config is half-filled fails to compile rather than falling back to somebody
 * else's default.
 */
data class SiteConfig(
    /** Origin, no trailing slash: `https://www.example.com`. */
    val baseUrl: String,
    /**
     * The User-Agent to send when the WebView cannot be asked what it is.
     *
     * Last resort only — [resolveUserAgent] reads the real one off the WebView, and a hardcoded UA
     * that contradicts the client hints Chromium keeps sending is how a managed challenge becomes an
     * infinite one. This value exists for the device where there is no WebView to ask.
     */
    val fallbackUserAgent: String,
    /** The `Accept` header page requests are made with. */
    val htmlAccept: String,
    /**
     * Cookie names that mean "signed in", any one of them.
     *
     * A guess by nature — a site does not document its session cookie — so nothing that reloads
     * content is allowed to depend on it. See [WebViewCookieJar.snapshot].
     */
    val sessionCookieNames: List<String>,
    val markers: PageMarkers,
)

/**
 * Text that classifies a 200-with-HTML the parsers cannot use.
 *
 * A forum behind Cloudflare answers a blocked request with a page, not a status code, so the body is
 * the only thing that says what happened. Each list is "any one of these appearing in the body means
 * this", and the order they are consulted in is [ChallengeDetector]'s, not this type's.
 */
data class PageMarkers(
    /**
     * Proof that this *is* the site rather than something served instead of it.
     *
     * Checked before the challenge markers and wins over them: Cloudflare inlines its
     * `/cdn-cgi/challenge-platform/` script into every page it proxies, challenge or not, so a real
     * page carries a challenge marker too. Content beats suspicion.
     */
    val usablePage: List<String>,
    /** The site's own wording for "this needs an account". */
    val loginRequired: List<String>,
    /** The site's own throttle sentence, which can arrive on a 200 as easily as on a 429. */
    val rateLimit: List<String>,
    /** Left at the default unless a site fronts itself with something other than Cloudflare. */
    val challenge: List<String> = CLOUDFLARE_CHALLENGE_MARKERS,
)

/**
 * Cloudflare's, not any one site's — which is why they are a default here rather than something each
 * site repeats in its own selector file.
 */
val CLOUDFLARE_CHALLENGE_MARKERS =
    listOf(
        "/cdn-cgi/challenge-platform/",
        "cf-browser-verification",
        "Just a moment...",
        "Checking your browser before accessing",
    )
