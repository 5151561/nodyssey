package io.github.plaza.core.net

import kotlinx.coroutines.delay

/**
 * Repeats a read that the edge refused with a gateway status, the way a reader who sees 503
 * presses reload.
 *
 * Why this exists, measured against NodeSeek on 2026-09-13: the site's edge answers a fraction of
 * this client's page and API reads with the origin's stock `503 Service Temporarily Unavailable`,
 * while a real browser on the same exit IP, same second, same URL, gets 200 — a browser's TLS and
 * HTTP/2 fingerprint scores as human, an HTTP library's does not, and the borderline verdict comes
 * out differently from one request to the next. A second try a moment later usually passes: 12
 * probes of the same board in a row ran 503, 200, 200, 503, 200 …, never two 503s from the same
 * URL more than a few seconds apart. So the reader was being handed a 重试 button for something
 * the app could have done itself before saying anything.
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
