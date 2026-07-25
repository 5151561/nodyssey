package io.github.nsreader.core.net

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

    /** An HTTP status we cannot use. */
    data class Http(val statusCode: Int) : NodeSeekError

    /** A 200 whose body contains none of the markers the parsers understand. */
    data object Unparsable : NodeSeekError

    /** Transport failure — no connection, timeout, TLS. */
    data object Network : NodeSeekError

    /** Anything we could not classify. */
    data object Unknown : NodeSeekError
}

/** Thrown by the data layer; carries [error] so the UI can pick both wording and recovery action. */
class NodeSeekException(
    val error: NodeSeekError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

/** True when a human can clear this by acting inside a WebView. */
val NodeSeekError.isRecoverableInBrowser: Boolean
    get() = this is NodeSeekError.Cloudflare || this is NodeSeekError.LoginRequired
