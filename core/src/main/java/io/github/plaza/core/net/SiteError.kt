package io.github.plaza.core.net

/**
 * Why a request could not be turned into content.
 *
 * Deliberately carries no user-facing text: the data layer must not decide wording or language.
 * The UI maps these onto string resources (see `SiteError.messageRes`).
 */
sealed interface SiteError {

    /** Cloudflare wants a browser to run its JS. Recoverable by opening the page in a WebView. */
    data object Cloudflare : SiteError

    /** The board or post requires a signed-in account. */
    data object LoginRequired : SiteError

    /**
     * The post is behind a reader-level floor this account has not reached.
     *
     * Apart from [LoginRequired] because the two have nothing in common but the wall: a site that
     * can tell the reader their level has already accepted their session, so the reader is signed in
     * and simply too low, and the only thing that clears it is whatever that site makes levels out
     * of — which no button on a screen of ours can do.
     *
     * [requiredLevel] is the level the page named, `null` when the page refused without naming one.
     */
    data class LevelRequired(val requiredLevel: Int?) : SiteError

    /**
     * The site's own throttle, not Cloudflare's.
     *
     * Kept apart from [Http] because the recovery is "wait a moment", not "retry now" — and apart
     * from [Cloudflare] because opening a WebView solves nothing. Which bodies mean this is the
     * site's to say: see [PageMarkers.rateLimit].
     */
    data object RateLimited : SiteError

    /** An HTTP status we cannot use. */
    data class Http(val statusCode: Int) : SiteError

    /** A 200 whose body contains none of the markers the parsers understand. */
    data object Unparsable : SiteError

    /** Transport failure — no connection, timeout, TLS. */
    data object Network : SiteError

    /**
     * The page exists on the site but the app has no endpoint for it.
     *
     * Not a failure of this request — nothing was sent. A screen in this state says so and offers the
     * web page instead of inventing rows. Distinct from [Unparsable], which means we *did* ask and
     * could not read the answer.
     */
    data object NotWired : SiteError

    /** Anything we could not classify. */
    data object Unknown : SiteError
}

/**
 * Thrown by the data layer; carries [error] so the UI can pick both wording and recovery action.
 *
 * [detail] is the server's own sentence when there is one — "对方已屏蔽你" and the like. Kept as a
 * property rather than folded into the message because a screen that can show it should not have to
 * guess whether [Exception.message] holds a reason or just the name of an [error] case.
 */
class SiteException(
    val error: SiteError,
    cause: Throwable? = null,
    val detail: String? = null,
) : Exception(detail ?: error.toString(), cause)

/** True when a human can clear this by acting inside a WebView. */
val SiteError.isRecoverableInBrowser: Boolean
    get() = this is SiteError.Cloudflare || this is SiteError.LoginRequired
