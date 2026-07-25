package io.github.nsreader.core.net

import io.github.nsreader.core.html.Selectors

/**
 * NodeSeek sits behind Cloudflare, and some boards require a signed-in account. Both failures come
 * back as a 200-with-HTML rather than a useful status code, so the body has to be inspected.
 *
 * Pure logic on purpose: it is covered by JVM unit tests against captured fixtures.
 */
object ChallengeDetector {

    fun detect(html: String, statusCode: Int, headers: Map<String, String>): Challenge? {
        if (Selectors.LOGIN_REQUIRED_MARKERS.any { html.contains(it) }) return Challenge.LoginRequired

        // A normal NodeSeek page also carries `Server: cloudflare`, so only an explicit challenge
        // signal counts — and real content always wins over a false positive.
        if (Selectors.USABLE_PAGE_MARKERS.any { html.contains(it) }) return null

        val normalized = headers.mapKeys { it.key.lowercase() }
        if (normalized["cf-mitigated"]?.lowercase() == "challenge") return Challenge.Cloudflare
        if (Selectors.CLOUDFLARE_MARKERS.any { html.contains(it) }) return Challenge.Cloudflare

        if (statusCode == 403 || statusCode == 503) return Challenge.Blocked(statusCode)
        if (statusCode !in 200..299) return Challenge.Blocked(statusCode)

        return Challenge.Unusable
    }

    sealed interface Challenge {
        /** Cloudflare wants a browser to run its JS; recoverable by opening the page in a WebView. */
        data object Cloudflare : Challenge

        /** The board or post needs an account. */
        data object LoginRequired : Challenge

        data class Blocked(val statusCode: Int) : Challenge

        /** A 200 that contains none of the markers we know how to parse. */
        data object Unusable : Challenge
    }
}

/** Thrown when a page could not be turned into content. The UI maps these onto recovery actions. */
class NodeSeekException(
    val challenge: ChallengeDetector.Challenge,
    message: String,
) : Exception(message) {

    companion object {
        fun of(challenge: ChallengeDetector.Challenge): NodeSeekException = NodeSeekException(
            challenge = challenge,
            message = when (challenge) {
                ChallengeDetector.Challenge.Cloudflare -> "需要通过 Cloudflare 验证"
                ChallengeDetector.Challenge.LoginRequired -> "该内容需要登录后查看"
                is ChallengeDetector.Challenge.Blocked -> "请求被拒绝：HTTP ${challenge.statusCode}"
                ChallengeDetector.Challenge.Unusable -> "页面内容无法解析"
            },
        )
    }
}
