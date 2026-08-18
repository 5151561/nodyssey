package io.github.plaza.core.image

import coil3.network.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Why an image did not arrive, reduced to the distinctions a reader can act on.
 *
 * A broken-image glyph says a picture is missing and nothing else, which is where post-879848
 * ended up: an attachment on a Cloudflare-fronted image host that every browser renders and the
 * app refuses, with no way from inside the app to tell that apart from a dead link, a typo in the
 * URL, or the phone being offline. These cases are the ones whose *fix* differs — retry, wait for
 * signal, or go look at it in a browser — so they are the ones worth naming.
 *
 * Wording lives with the UI. This is `:core`, which has no strings.
 */
sealed interface ImageLoadFailure {
    /**
     * The host would rather talk to a browser: it answered with a Cloudflare challenge.
     *
     * Retrying is pointless — the next request looks exactly like this one — and that is the whole
     * value of separating it from [Http]. Opening the image in a browser is what works, because a
     * browser is what the challenge is asking for.
     */
    data class Challenge(val code: Int) : ImageLoadFailure

    /** The host answered, and the answer was not an image. */
    data class Http(val code: Int) : ImageLoadFailure

    /** The host could not be resolved or reached at all — usually no connectivity. */
    data object Unreachable : ImageLoadFailure

    /** A connection was made and then ran out of time. */
    data object Timeout : ImageLoadFailure

    /** Some other transport failure: reset, TLS, a socket closed mid-body. */
    data object Connection : ImageLoadFailure

    /**
     * Bytes arrived and did not become an image, or something failed that is none of the above.
     *
     * Deliberately vague. Coil raises a decode failure as a plain exception with no type of its
     * own, so anything more specific here would be a guess dressed up as a diagnosis.
     */
    data object Unknown : ImageLoadFailure
}

/**
 * Classifies the throwable Coil hands back on [coil3.request.ErrorResult].
 *
 * Callers deal with `ImagesDeferredException` — an image the app declined to fetch — before they
 * get here; a skipped image is not a failure and must not be described as one.
 */
fun diagnoseImageFailure(throwable: Throwable?): ImageLoadFailure =
    when (throwable) {
        is HttpException -> {
            val code = throwable.response.code
            if (throwable.response.isChallenge()) ImageLoadFailure.Challenge(code) else ImageLoadFailure.Http(code)
        }

        is UnknownHostException -> ImageLoadFailure.Unreachable

        // `InterruptedIOException` covers OkHttp's own call timeout, which is not a socket timeout
        // and does not extend it. Both are "it took too long" to a reader.
        is SocketTimeoutException, is InterruptedIOException -> ImageLoadFailure.Timeout

        is IOException -> ImageLoadFailure.Connection

        else -> ImageLoadFailure.Unknown
    }

/**
 * True when Cloudflare says it interposed a challenge on this response.
 *
 * `cf-mitigated: challenge` is the header it sets on the interstitial, and it is the only signal
 * used: a normal response from any site behind Cloudflare also carries `server: cloudflare` and a
 * `cf-ray`, so those cannot separate a challenge from an ordinary 403 the host meant to send.
 *
 * Read out of [coil3.network.NetworkHeaders.asMap] and matched case-insensitively here rather than
 * through `get`, so the answer does not depend on how Coil chooses to key its own map.
 */
private fun coil3.network.NetworkResponse.isChallenge(): Boolean =
    headers
        .asMap()
        .entries
        .firstOrNull { (name, _) -> name.equals("cf-mitigated", ignoreCase = true) }
        ?.value
        ?.any { it.equals("challenge", ignoreCase = true) } == true
