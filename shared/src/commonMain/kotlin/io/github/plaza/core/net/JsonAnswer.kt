package io.github.plaza.core.net

/**
 * Whether Cloudflare answered a JSON endpoint instead of the site.
 *
 * [ChallengeDetector] is this for HTML pages, and the order it checks in is the whole lesson:
 * [PageMarkers.usablePage] wins over every challenge signal, because "real content always wins over
 * a false positive". The JSON path had no such guard and carried a rule of its own — "an HTML body
 * on a JSON endpoint means Cloudflare intercepted the call" — which is false on a forum whose front
 * end is a single-page app: a path its API router does not recognise is answered with `index.html`,
 * so a moved endpoint comes back as **the site's own page**, and every screen that called it put
 * 需要验证 over it. Tapping 验证 opened a web view on a site with no challenge to solve, which showed
 * the reader the home page and left them where they started.
 *
 * Dropping that rule was not enough, and this is the part worth remembering: Cloudflare injects its
 * `/cdn-cgi/challenge-platform/` script into *ordinary* 200s wherever JS detection is switched on, so
 * [PageMarkers.challenge] matches pages that are not challenges. Only the same guard the HTML path
 * already had settles it — a body carrying the site's own markers is the site talking, whatever
 * Cloudflare's script tags are doing inside it.
 *
 * What is left after the guard is a genuine signal: the `cf-mitigated` header, which is
 * authoritative, and the challenge page's own markup — and only its own: a body with no site markers
 * at all, such as the origin's stock 503 page, still carries the injected script, which is why
 * [CLOUDFLARE_CHALLENGE_MARKERS] names the interstitial's paths rather than the shared prefix.
 */
fun isChallengeAnswer(
    body: String,
    cfMitigated: String?,
    markers: PageMarkers,
): Boolean {
    if (markers.usablePage.any(body::contains)) return false
    return cfMitigated?.equals("challenge", ignoreCase = true) == true ||
        markers.challenge.any(body::contains)
}

/**
 * Whether the answer is a web page rather than data.
 *
 * Checked *after* the status, never before it: a 403 that carries a page is still a 403, and the
 * recovery the reader needs for it is the one the status names. What is left for this to catch is
 * the case with no status to speak for it — a 200 carrying a page.
 */
fun isHtmlAnswer(body: String): Boolean = body.trimStart().startsWith("<")

/**
 * The `<title>` of a page that arrived where data was expected, for the message that reports it.
 *
 * Read with a substring rather than a parser because it is a diagnostic, not content: `NodeSeek`
 * says the SPA shell came back, `Just a moment...` says it really was Cloudflare after all, and
 * either one turns "some endpoint is broken" into a name.
 */
fun htmlTitle(body: String): String? {
    val open = body.indexOf("<title", ignoreCase = true).takeIf { it >= 0 } ?: return null
    val start = body.indexOf('>', open).takeIf { it > 0 }?.plus(1) ?: return null
    val end = body.indexOf("</title", start, ignoreCase = true).takeIf { it > start } ?: return null
    return body.substring(start, end).trim().take(MAX_TITLE).ifBlank { null }
}

private const val MAX_TITLE = 60
