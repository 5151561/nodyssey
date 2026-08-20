package io.github.plaza.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.appendData
import platform.Foundation.dataTaskWithURL
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * One GET, read as bytes rather than as text.
 *
 * [HttpTransport] is deliberately the only network contract above this layer, and it hands back a
 * decoded `String` because everything above it reads HTML or JSON. A picture is the one thing that is
 * neither, and this is the Apple side of that exception — the Android side is `OkHttpOfflineImageSource`
 * reaching for its `OkHttpClient` directly, for exactly the same reason.
 *
 * The session is the app's own, so a picture arrives under the same cookies, `User-Agent` and
 * `Accept-Language` as the page it was embedded in. That is not a nicety: the attachment host is
 * behind Cloudflare and answers a request missing those with a challenge page.
 *
 * @param maxBytes a ceiling the body is held *to*, not measured against afterwards — see the two
 *   shapes below. null for a caller with no limit, which is the on-screen image loader: it is drawing
 *   what the reader is already looking at, and a picture too big to hold is a picture too big to show.
 * @return null for any answer that is not a 2xx with a body, and for a body that went over [maxBytes].
 */
suspend fun NSURLSession.getBytes(url: String, maxBytes: Long? = null): UrlSessionBytes? {
    val target = NSURL.URLWithString(url) ?: return null

    // Two shapes, and the difference is where the bytes are while the decision is being made.
    //
    // With no ceiling, `dataTaskWithURL:completionHandler:` is the whole of it: the body is handed
    // over complete, which is what a caller with no limit was going to ask for anyway.
    //
    // With one, that convenience is the bug. Its completion handler is called with the body *already*
    // in an `NSData` — so checking `expectedContentLength` there reads a number that arrives after
    // the megabytes it describes are in memory, which is precisely the case a ceiling exists to
    // prevent. A background store of a 40 MB screenshot would be refused, correctly, having first
    // held all 40 MB. So the bounded read takes the delegate: the declared length is checked at the
    // response header and the load refused before a byte of body is accepted, and a server that
    // declares nothing is stopped mid-stream the moment the running total goes over.
    if (maxBytes == null) {
        return suspendCancellableCoroutine { continuation ->
            val task =
                dataTaskWithURL(target) { data: NSData?, response, _ ->
                    val http = response as? NSHTTPURLResponse
                    continuation.resume(
                        if (data == null || http == null || !http.isOk()) null else http.readAs(data),
                    )
                }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    // The session's own delegate, carried over: a task delegate supersedes it, and Apple documents the
    // session's as the fallback only for methods the task's does not implement. `BoundedBodyDelegate`
    // extends it rather than relying on that, which is the same call `ForumSessionDelegate`'s KDoc
    // makes for the upload path — except that this one has a password to inherit as well.
    val inherited = (delegate as? ForumSessionDelegate)?.proxyCredential
    return suspendCancellableCoroutine { continuation ->
        val task = dataTaskWithURL(target)
        task.setDelegate(
            BoundedBodyDelegate(maxBytes = maxBytes, proxyCredential = inherited) { continuation.resume(it) },
        )
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

/**
 * Reads a body under a ceiling, and refuses it at the earliest point the ceiling can be known to be
 * broken.
 *
 * Two points, because a server may or may not say how much it is about to send. `didReceiveResponse`
 * is the cheap one — a declared `Content-Length` over the limit is answered with
 * `NSURLSessionResponseCancel`, and no body is transferred at all. `didReceiveData` is the one that
 * catches the rest: chunked answers, and servers that lie. The buffer therefore never holds more than
 * the limit plus the chunk that crossed it.
 *
 * The two task-level jobs are forwarded to a [ForumSessionDelegate] rather than left to the session's
 * own, because installing a task delegate takes them away from it and this path needs both: an image
 * host's 302 to a CDN is the reason the `Referer` stripping exists at all. Forwarding rather than
 * subclassing because Kotlin/Native rejects a non-final subclass of an Objective-C class.
 */
internal class BoundedBodyDelegate(
    private val maxBytes: Long,
    proxyCredential: NSURLCredential?,
    private val onFinished: (UrlSessionBytes?) -> Unit,
) : NSObject(),
    NSURLSessionDataDelegateProtocol {

    private val forum = ForumSessionDelegate(proxyCredential = proxyCredential)
    private val buffer = NSMutableData()
    private var response: NSHTTPURLResponse? = null
    private var refused = false
    private var finished = false

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) = forum.URLSession(session, task, willPerformHTTPRedirection, newRequest, completionHandler)

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) = forum.URLSession(session, task, didReceiveChallenge, completionHandler)

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit,
    ) {
        val http = didReceiveResponse as? NSHTTPURLResponse
        response = http
        val declared = http?.expectedContentLength ?: -1L
        // -1 is `NSURLResponseUnknownLength`: the server said nothing, so there is nothing to refuse
        // on yet and the running total below is the only guard left.
        refused = http == null || !http.isOk() || (declared >= 0 && declared > maxBytes)
        completionHandler(if (refused) NSURLSessionResponseCancel else NSURLSessionResponseAllow)
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        if (refused) return
        if (buffer.length.toLong() + didReceiveData.length.toLong() > maxBytes) {
            refused = true
            dataTask.cancel()
            return
        }
        buffer.appendData(didReceiveData)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        // A refusal above cancels the task, which arrives back here as an error; guarded anyway,
        // because this is the one callback that must fire exactly once for the continuation's sake.
        if (finished) return
        finished = true
        val http = response
        onFinished(
            if (refused || didCompleteWithError != null || http == null) null else http.readAs(buffer),
        )
    }
}

private fun NSHTTPURLResponse.isOk(): Boolean = statusCode.toInt() in 200..299

private fun NSHTTPURLResponse.readAs(data: NSData): UrlSessionBytes =
    UrlSessionBytes(
        data = data,
        // Absent, or -1 when the server did not say — which is the case this exists to distinguish,
        // since a caller with a size limit has to know whether it is being told anything at all.
        declaredLength = expectedContentLength.takeIf { it >= 0 },
        mimeType = MIMEType,
        headers =
        allHeaderFields.entries.mapNotNull { entry ->
            val name = entry.key as? String ?: return@mapNotNull null
            val value = entry.value as? String ?: return@mapNotNull null
            name to value
        },
    )

/**
 * What [getBytes] read.
 *
 * [headers] is carried out whole rather than reduced to the two fields above it, because the caller
 * that needs them is a cache: `Cache-Control`, `ETag` and `Last-Modified` are the difference between
 * an image store that revalidates and one that holds its first answer forever. The first version of
 * this shell dropped them, and the way that showed up was a picture the server had since replaced —
 * a hotlink-protection notice — still being drawn after the header that caused it was fixed.
 */
class UrlSessionBytes(
    val data: NSData,
    val declaredLength: Long?,
    val mimeType: String?,
    val headers: List<Pair<String, String>> = emptyList(),
)
