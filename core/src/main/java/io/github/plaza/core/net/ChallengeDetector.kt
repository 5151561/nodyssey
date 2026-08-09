package io.github.plaza.core.net

/**
 * A scraped forum sits behind Cloudflare, and some of its boards require a signed-in account. Both
 * failures come back as a 200-with-HTML rather than a useful status code, so the body has to be
 * inspected.
 *
 * Pure logic on purpose: it is covered by JVM unit tests against captured fixtures. What counts as
 * which failure is [markers], because only the site knows its own wording.
 */
class ChallengeDetector(private val markers: PageMarkers) {

    /** Returns the reason this response is unusable, or `null` when it carries real content. */
    fun detect(html: String, statusCode: Int, headers: Map<String, String>): SiteError? {
        // Before the login markers, not after: see [PageMarkers.levelRequired] — the two refusals
        // can share a phrase, and only the more specific one carries a level to show.
        markers.levelRequired.firstNotNullOfOrNull { it.find(html) }?.let { match ->
            return SiteError.LevelRequired(match.groupValues.getOrNull(1)?.toIntOrNull())
        }

        if (markers.loginRequired.any { html.contains(it) }) return SiteError.LoginRequired

        // A normal page from a Cloudflare-fronted site also carries `Server: cloudflare`, so only an
        // explicit challenge signal counts — and real content always wins over a false positive.
        if (markers.usablePage.any { html.contains(it) }) return null

        val normalized = headers.mapKeys { it.key.lowercase() }
        if (normalized["cf-mitigated"]?.lowercase() == "challenge") return SiteError.Cloudflare
        if (markers.challenge.any { html.contains(it) }) return SiteError.Cloudflare

        // After the Cloudflare checks, before the generic status handling: a 429 that carried a
        // challenge is Cloudflare's, everything else is the site's own throttle, and "HTTP 429" as a
        // screen title tells the reader nothing they can act on.
        if (statusCode == 429 || markers.rateLimit.any { html.contains(it) }) {
            return SiteError.RateLimited
        }

        if (statusCode !in 200..299) return SiteError.Http(statusCode)

        return SiteError.Unparsable
    }
}
