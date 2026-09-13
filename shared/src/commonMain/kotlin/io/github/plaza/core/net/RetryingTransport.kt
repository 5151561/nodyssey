package io.github.plaza.core.net

import kotlinx.coroutines.delay

/**
 * Repeats a read that the edge refused with a gateway status, the way a reader who sees 503
 * presses reload.
 *
 * Why this exists, measured against NodeSeek on 2026-09-13: with the site's `sortBy` cookie kept off
 * the request (see [SiteConfig.unreplayableCookies], which was the deterministic 503), a signed-out
 * read of a board still came back as the origin's stock `503 Service Temporarily Unavailable` now
 * and then — the same URL answered 503, then 200 three seconds later, on both `/categories/daily`
 * and `/categories/photo-share`, from a Mac probe and from the phone alike. Whatever the origin's
 * reason, a second try a moment later passes, and the reader was being handed a 重试 button for
 * something the app could have done itself before saying anything.
 *
 * Only `GET`s, and only [GATEWAY_STATUSES]. A write that came back 503 may well have been applied,
 * and sending it again is how a comment gets posted twice; a 4xx is the site's answer, not the
 * edge's mood, and repeating the question does not change it — a 403 that carries
 * `cf-mitigated: challenge` in particular is Cloudflare asking for a browser, which no amount of
 * asking again supplies (see [ChallengeDetector]). Transport failures are left to the platform
 * client, which already retries a dropped connection and should not be second-guessed on a timeout.
 *
 * Two retries with a short, doubling pause: enough to ride out the verdict flapping, few enough that
 * a real outage still reports itself within a couple of seconds rather than hanging a spinner.
 */
class RetryingTransport(
    private val delegate: HttpTransport,
    private val maxRetries: Int = DEFAULT_RETRIES,
    /** The pause before retry number `attempt` (1-based). Injectable so a test does not wait. */
    private val pauseMillis: (attempt: Int) -> Long = { attempt -> BASE_PAUSE_MILLIS shl (attempt - 1) },
) : HttpTransport {

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        if (!request.method.equals("GET", ignoreCase = true)) {
            return delegate.execute(request, onUploadProgress)
        }
        var attempt = 0
        while (true) {
            val response = delegate.execute(request, onUploadProgress)
            if (!response.isRetryable() || attempt == maxRetries) return response
            attempt++
            delay(pauseMillis(attempt))
        }
    }

    private fun HttpResponse.isRetryable(): Boolean =
        code in GATEWAY_STATUSES && header("cf-mitigated") == null

    companion object {
        const val DEFAULT_RETRIES = 2
        const val BASE_PAUSE_MILLIS = 600L

        /** The statuses that mean "not now" rather than "no": the edge or the origin, not the site's answer. */
        val GATEWAY_STATUSES: IntRange = 502..504
    }
}
