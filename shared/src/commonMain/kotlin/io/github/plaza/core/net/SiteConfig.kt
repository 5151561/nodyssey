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
     * content is allowed to depend on it. See [SessionCookies.snapshot].
     */
    val sessionCookieNames: List<String>,
    val markers: PageMarkers,
    /**
     * The cookie the site's own front end writes to remember light or dark, when it has one.
     *
     * Not a nicety, and not about colour. NodeSeek's account endpoint omits a user's readme from its
     * answer unless the request carries this cookie — measured 2026-09-02: same URL, same
     * `?readme=1`, cookie present and `detail.readme` is there, cookie absent and every other field
     * still is. Any value does, an empty one included, and no other cookie substitutes for it. A
     * client that never writes it therefore draws 「没有找到readme」on every profile in the site,
     * which is exactly what the iOS shell did on a fresh install.
     *
     * The value is still the site's own vocabulary rather than a placeholder, because this cookie is
     * shared with the in-app browser: the same jar decides what the sign-in page looks like, and a
     * reader in dark mode should not be handed a white page. See [SessionCookies.applyColorScheme].
     */
    val colorSchemeCookie: ColorSchemeCookie? = null,
)

/**
 * The name a site remembers its light/dark choice under, and the two values it writes.
 *
 * Both values, rather than a boolean and a guess: what a site calls its dark mode is the site's
 * business, and the endpoint quirk above only cares that *something* is there.
 */
data class ColorSchemeCookie(
    val name: String,
    val light: String,
    val dark: String,
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
    /**
     * The site's own wording for "your level is too low", consulted *before* [loginRequired].
     *
     * Order is the point: a forum that words both refusals as some flavour of 权限不足 would otherwise
     * have every level wall read as a login wall, and offer a signed-in reader the sign-in page.
     *
     * Patterns rather than plain strings because the level is a number *inside* the sentence, and a
     * screen that can name it says something the reader can act on. The first group of whichever
     * pattern matches first is that number; a pattern with no group classifies without one, which is
     * why a list that carries both should put the capturing one first.
     */
    val levelRequired: List<Regex> = emptyList(),
    /**
     * The site's own wording for "the author sealed this thread", which no reader level clears.
     *
     * Separate from [levelRequired] because the two refusals only look alike: one names a floor a
     * reader can climb to, this one names none because there is none. A plain string rather than a
     * pattern for the same reason — there is no number in the sentence to read.
     */
    val privatePost: List<String> = emptyList(),
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
