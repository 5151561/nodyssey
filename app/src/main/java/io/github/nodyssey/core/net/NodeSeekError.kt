package io.github.nodyssey.core.net

/**
 * Why a request could not be turned into content.
 *
 * Deliberately carries no user-facing text: the data layer must not decide wording or language.
 * The UI maps these onto string resources (see `NodeSeekError.messageRes`).
 */
sealed interface NodeSeekError {

    /** Cloudflare wants a browser to run its JS. Recoverable by opening the page in a WebView. */
    data object Cloudflare : NodeSeekError

    /** The board or post requires a signed-in account. */
    data object LoginRequired : NodeSeekError

    /**
     * NodeSeek's own throttle, not Cloudflare's.
     *
     * `/search` answers a second request inside two seconds with **HTTP 429** and the body
     * `{"success":false,"message":"每隔2秒可以操作一次"}` (verified live 2026-08-01). Kept apart from
     * [Http] because the recovery is "wait a moment", not "retry now" — and apart from [Cloudflare]
     * because opening a WebView solves nothing.
     */
    data object RateLimited : NodeSeekError

    /** An HTTP status we cannot use. */
    data class Http(val statusCode: Int) : NodeSeekError

    /** A 200 whose body contains none of the markers the parsers understand. */
    data object Unparsable : NodeSeekError

    /** Transport failure — no connection, timeout, TLS. */
    data object Network : NodeSeekError

    /**
     * The page exists on the site but the app has no endpoint for it.
     *
     * Not a failure of this request — nothing was sent. A screen in this state says so and offers the
     * web page instead of inventing rows. Distinct from [Unparsable], which means we *did* ask and
     * could not read the answer.
     *
     * `/credit`, `/stardust/list`, `/fans` and `/ruling` were all here once; each left when its
     * contract was read out of the site's own bundle rather than guessed. No screen answers this
     * today — the one caller left is a reaction write on a page that arrived without the `__config__`
     * the write needs.
     */
    data object NotWired : NodeSeekError

    /** Anything we could not classify. */
    data object Unknown : NodeSeekError
}

/**
 * Thrown by the data layer; carries [error] so the UI can pick both wording and recovery action.
 *
 * [detail] is the server's own sentence when there is one — "对方已屏蔽你" and the like. Kept as a
 * property rather than folded into the message because a screen that can show it should not have to
 * guess whether [Exception.message] holds a reason or just the name of an [error] case.
 */
class NodeSeekException(
    val error: NodeSeekError,
    cause: Throwable? = null,
    val detail: String? = null,
) : Exception(detail ?: error.toString(), cause)

/** True when a human can clear this by acting inside a WebView. */
val NodeSeekError.isRecoverableInBrowser: Boolean
    get() = this is NodeSeekError.Cloudflare || this is NodeSeekError.LoginRequired
