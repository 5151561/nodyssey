package io.github.nodyssey.core.net

import io.github.nodyssey.core.html.Selectors

/**
 * NodeSeek sits behind Cloudflare, and some boards require a signed-in account. Both failures come
 * back as a 200-with-HTML rather than a useful status code, so the body has to be inspected.
 *
 * Pure logic on purpose: it is covered by JVM unit tests against captured fixtures.
 */
object ChallengeDetector {

    /** Returns the reason this response is unusable, or `null` when it carries real content. */
    fun detect(html: String, statusCode: Int, headers: Map<String, String>): NodeSeekError? {
        if (Selectors.LOGIN_REQUIRED_MARKERS.any { html.contains(it) }) return NodeSeekError.LoginRequired

        // A normal NodeSeek page also carries `Server: cloudflare`, so only an explicit challenge
        // signal counts — and real content always wins over a false positive.
        if (Selectors.USABLE_PAGE_MARKERS.any { html.contains(it) }) return null

        val normalized = headers.mapKeys { it.key.lowercase() }
        if (normalized["cf-mitigated"]?.lowercase() == "challenge") return NodeSeekError.Cloudflare
        if (Selectors.CLOUDFLARE_MARKERS.any { html.contains(it) }) return NodeSeekError.Cloudflare

        if (statusCode !in 200..299) return NodeSeekError.Http(statusCode)

        return NodeSeekError.Unparsable
    }
}
