package io.github.plaza.core.net

/**
 * One HTTP call and its answer, described in terms no platform owns.
 *
 * This is the line the first phase drew at `HtmlSource` moved down one level. `HtmlSource` and
 * [io.github.nodyssey.core.net.JsonApi] say what the *site* is asked for; this says what a request
 * is, and it is the only thing under them that a platform has to answer for. OkHttp on Android,
 * `NSURLSession` on Apple, a map in a test — everything above this file is the same code on all
 * three.
 *
 * Deliberately small. It carries no timeouts, no proxy, no cookie jar and no redirect policy,
 * because every one of those is configured once when a client is built and none of them varies per
 * call. A transport arrives already knowing them; a caller never gets to say.
 */
interface HttpTransport {
    /**
     * Sends [request] and reads the whole answer.
     *
     * Throws [SiteException] with [SiteError.Network] when the call did not complete — a DNS
     * failure, a refused connection, a timeout. A completed call with an unhappy status is an
     * answer, not a failure, and comes back as an [HttpResponse] for the caller to read.
     *
     * @param onUploadProgress see [UploadProgress]. Null for every caller that is not an upload,
     *   which is all but one of them.
     */
    suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress? = null): HttpResponse
}

/**
 * How much of a request body has gone out, `0f`–`1f`.
 *
 * The one thing in this file that is about a request *while it is still in flight*, and it is here
 * under protest: everything else on [HttpTransport] describes a finished call, which is the shape
 * that let two platforms answer for the same contract. An upload is the one caller that needs the
 * other thing — the attachment tray draws a ring, and a ring that only knows 0% and 100% is a
 * decoration. It stayed out of `commonMain` for exactly this reason until the six image-host
 * clients needed to run on a platform with no OkHttp in it.
 *
 * Called on whatever thread the platform reports progress on, which is not the caller's: OkHttp
 * calls it from the thread writing the sink, `NSURLSession` from its delegate queue. A listener
 * that touches UI state has to hop for itself — [io.github.nodyssey.data.composer.ImageUploader]
 * does.
 *
 * A transport is allowed to never call this (a body small enough to go out in one write), but one
 * that calls it at all must finish at `1f`.
 */
typealias UploadProgress = (fraction: Float) -> Unit

/**
 * @property url absolute; resolving a site-relative path is the caller's business — see
 *   [io.github.plaza.core.net.SiteConfig] and `NodeSeekSite`.
 * @property headers set as given. A transport may add what a platform always sends (`Host`,
 *   `Accept-Encoding`) and what its client was configured with, but must not overrule these.
 */
data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: HttpBody? = null,
)

/** What a request carries, in the three shapes this app actually sends. */
sealed interface HttpBody {
    /** A body with no bytes, which is not the same as no body: a POST without one is malformed. */
    data object Empty : HttpBody

    data class Text(
        val content: String,
        val contentType: String = "application/json; charset=utf-8",
    ) : HttpBody

    /**
     * One `multipart/form-data` upload: text fields, and exactly one file.
     *
     * One file rather than a list because that is what every caller sends — an avatar, a post
     * attachment — and a shape that admits more would have to answer what order they go in.
     */
    data class Multipart(
        val fields: Map<String, String>,
        val fileField: String,
        val fileName: String,
        val fileBytes: ByteArray,
        val fileMimeType: String,
    ) : HttpBody {
        // `ByteArray` compares by identity, which would make two equal uploads unequal and make the
        // generated `hashCode` change between runs. Written out because a data class holding one is
        // otherwise quietly wrong.
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is Multipart &&
                        fields == other.fields &&
                        fileField == other.fileField &&
                        fileName == other.fileName &&
                        fileMimeType == other.fileMimeType &&
                        fileBytes.contentEquals(other.fileBytes)
                    )

        override fun hashCode(): Int {
            var result = fields.hashCode()
            result = 31 * result + fileField.hashCode()
            result = 31 * result + fileName.hashCode()
            result = 31 * result + fileMimeType.hashCode()
            result = 31 * result + fileBytes.contentHashCode()
            return result
        }
    }
}

/**
 * @property url where the answer came from, which is the requested URL unless a redirect moved it.
 *   [HtmlSource.resolveRedirect] is the reason it is here — the site answers a `/member?t=` lookup
 *   with a 302 whose destination is the answer.
 * @property headers keys lowercased, one entry per name, repeats joined with `,`. Lowercased here
 *   rather than at every reader because HTTP header names are case-insensitive and two platforms
 *   disagree about which case they hand back.
 * @property body decoded as text. Every endpoint this app calls answers with HTML or JSON; a
 *   transport that has to stream bytes — the APK download — does not come through here.
 */
data class HttpResponse(
    val code: Int,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
) {
    val isSuccessful: Boolean get() = code in 200..299

    fun header(name: String): String? = headers[name.lowercase()]
}
