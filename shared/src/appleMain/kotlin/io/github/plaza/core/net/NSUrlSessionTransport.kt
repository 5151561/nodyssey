@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.plaza.core.net

import io.github.plaza.core.toNSData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.appendData
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.uploadTaskWithRequest
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [HttpTransport] on `NSURLSession` — the Apple side of the one contract everything above the
 * network layer is written against.
 *
 * The session is asked for per call rather than held, and that is the whole of what the proxy costs
 * on this platform. OkHttp takes a `ProxySelector` and consults it on every request, so one client
 * lasts a lifetime; `NSURLSession` takes its proxy in `connectionProxyDictionary`, which is part of
 * the configuration a session is *created* from and cannot be changed afterwards. So a saved edit in
 * 代理设置 means a new session, and this asks for the current one each time instead of keeping the
 * one it was built with. See [ProxiedUrlSession].
 *
 * Everything else is still configured once and never varies per call: the cookie storage, the
 * timeouts, the default headers. [appleUrlSession] is the configuration this app uses, and it is a
 * function rather than a default argument so that a caller has to have looked at it.
 */
class NSUrlSessionTransport(
    private val session: () -> NSURLSession,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        val url = NSURL.URLWithString(request.url) ?: throw SiteException(SiteError.Network)
        val call = NSMutableURLRequest(uRL = url)
        call.setHTTPMethod(request.method)
        request.headers.forEach { (name, value) -> call.setValue(value, forHTTPHeaderField = name) }
        val body = request.body
        // Only when the body names one. `setValue(null, …)` *removes* a header, which would quietly
        // undo a `Content-Type` the caller put in [HttpRequest.headers] alongside an empty body.
        body?.contentType()?.let { call.setValue(it, forHTTPHeaderField = "Content-Type") }

        val session = session()
        return suspendCancellableCoroutine { continuation ->
            val completion = { data: NSData?, response: platform.Foundation.NSURLResponse?, error: platform.Foundation.NSError? ->
                when {
                    error != null ->
                        continuation.resumeWithException(
                            // Everything `NSURLSession` reports here is a call that did not
                            // complete, which is exactly what `Network` means; the description is
                            // kept as the detail so a log says which one.
                            SiteException(SiteError.Network, detail = error.localizedDescription),
                        )

                    response !is NSHTTPURLResponse ->
                        continuation.resumeWithException(SiteException(SiteError.Network))

                    else ->
                        continuation.resume(
                            HttpResponse(
                                code = response.statusCode.toInt(),
                                // After redirects: `NSURLSession` follows them itself and the
                                // response carries the URL it ended on.
                                url = response.URL?.absoluteString ?: request.url,
                                headers =
                                response.allHeaderFields.entries.associate { (name, value) ->
                                    name.toString().lowercase() to value.toString()
                                },
                                body = data?.decodeUtf8().orEmpty(),
                            ),
                        )
                }
            }

            val task =
                if (onUploadProgress != null && body != null) {
                    // An upload task, and only when somebody is listening. `didSendBodyData` is
                    // reported for any task with a body, but a data task carries its bytes in
                    // `HTTPBody`, which the documentation says is read into memory and re-sent
                    // wholesale on a retry; `fromData:` is the shape this platform means for a body
                    // being watched. The listener is a *task* delegate rather than the session's,
                    // because the session outlives this call and is shared with every other one.
                    session.uploadTaskWithRequest(call, fromData = body.toNSData(), completionHandler = completion)
                        .also { it.setDelegate(ForumSessionDelegate(onUploadProgress = onUploadProgress)) }
                } else {
                    body?.let { call.setHTTPBody(it.toNSData()) }
                    session.dataTaskWithRequest(call, completionHandler = completion)
                }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }
}

/**
 * The session this app talks to a scraped forum through.
 *
 * `defaultSessionConfiguration` rather than `ephemeral`: the shared storage persists across
 * launches, and it is the jar the WebKit bridge deposits the browser's cookies into. That is the
 * reason, and it is worth stating exactly because the tempting one — "the sign-in browser writes
 * there" — is false; see [AppleCookieStore].
 *
 * The three headers are the ones `BrowserHeadersInterceptor` fills in on the OkHttp side, and each
 * is there because leaving it out has cost this app a visible failure. The [referer] is the one that
 * needs the delegate below: some image hosts serve *only* requests referred by the forum, and others
 * refuse any request that still carries one after a redirect. See [ForumSessionDelegate].
 *
 * @param referer null for a third-party host — an API key's host has no part in the forum's browsing
 *   session and should not be told about it. The forum's own client passes the site's base URL.
 * @param cookies null for a third-party host too, and for the same reason one level down: a session
 *   with no storage sends no cookies and keeps none, so the forum's session cannot ride along to an
 *   image host holding a bearer-equivalent secret. This is the Apple half of "the image-host client
 *   has no cookie jar" in `DefaultAppContainer`.
 * @param proxy null for a direct connection. A session cannot be re-pointed once built, which is why
 *   this is an argument here and a `ProxySelector` on the other platform — see [ProxiedUrlSession].
 */
fun appleUrlSession(
    userAgent: String,
    acceptLanguage: String,
    referer: String? = null,
    timeoutSeconds: Double = 20.0,
    cookies: NSHTTPCookieStorage? = NSHTTPCookieStorage.sharedHTTPCookieStorage,
    proxy: ProxyRoute? = null,
): NSURLSession {
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration
    configuration.HTTPCookieStorage = cookies
    configuration.HTTPShouldSetCookies = cookies != null
    configuration.timeoutIntervalForRequest = timeoutSeconds
    configuration.HTTPAdditionalHeaders =
        buildMap<Any?, Any?> {
            put("User-Agent", userAgent)
            put("Accept-Language", acceptLanguage)
            if (referer != null) put("Referer", referer)
        }
    proxy?.let { configuration.connectionProxyDictionary = it.connectionProxyDictionary() }
    return NSURLSession.sessionWithConfiguration(
        configuration,
        delegate = ForumSessionDelegate(proxyCredential = proxy?.credential()),
        delegateQueue = null,
    )
}

/**
 * Drops `Referer` once a redirect has carried a request to another host, answers a proxy that asks
 * for a password, and counts an upload's bytes out.
 *
 * Three unrelated jobs in one class because a task delegate and a session delegate are the same
 * protocol, and a task that installs its own would otherwise lose the session's. Apple documents the
 * session delegate as the fallback for methods a task delegate does not implement; implementing all
 * three here means the answer does not depend on that being true.
 *
 * **Referer.** The Apple half of `CrossOriginRefererInterceptor`, and the same two hosts are behind
 * it. Hotlink protection is one of them: an image host that answers a direct request with
 * 「只允许将图片嵌入网页」 and a 403 needs the referrer on the first hop. The other is the opposite —
 * `pic1.imgdb.cn` sends `Referrer-Policy: no-referrer` and a 302 to a Baidu CDN that refuses anything
 * still carrying `Referer: nodeseek.com`. A browser drops it on that hop; `NSURLSession`, like OkHttp,
 * carries it through unless told otherwise, and this is where it is told. Host, not origin: an
 * `http` → `https` upgrade of the same host is not the situation this is about, and hotlink
 * protection is keyed on the host anyway.
 *
 * A task delegate rather than anything the caller does: redirects are followed inside the session, so
 * this is the only place a hop that the caller never asked for can be seen. It is still called when
 * the task was created with a completion handler — only the data and response callbacks are the ones
 * that handler takes over.
 *
 * **The proxy password.** `AppProxyAuthenticator`'s counterpart. `connectionProxyDictionary` carries
 * a SOCKS credential itself but has no key for an HTTP proxy's, which arrives as a `407` and comes
 * through here instead. [NSURLAuthenticationChallenge.previousFailureCount] is what keeps a wrong
 * password from being offered forever — the same reasoning as the `Proxy-Authorization` check on the
 * other side.
 */
internal class ForumSessionDelegate(
    // Readable for one caller: the bounded read in `UrlSessionBytes.kt` has to install a *task*
    // delegate to see the body arrive, and it forwards the two jobs above to an instance of this
    // class rather than reimplementing them — which it cannot do without the password. Forwarding
    // rather than subclassing because Kotlin/Native does not allow a non-final subclass of an
    // Objective-C class, and this one is not final-able while it is also a delegate.
    internal val proxyCredential: NSURLCredential? = null,
    private val onUploadProgress: UploadProgress? = null,
) : NSObject(),
    NSURLSessionTaskDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        val addressed = task.originalRequest?.URL?.host
        val hop = newRequest.URL?.host
        if (addressed == null || hop == null || hop == addressed) {
            completionHandler(newRequest)
            return
        }
        val stripped = newRequest.mutableCopy() as NSMutableURLRequest
        stripped.setValue(null, forHTTPHeaderField = "Referer")
        completionHandler(stripped)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        val credential = proxyCredential
        // Not ours to answer: a server's own `401`, a TLS trust decision, or a proxy this session was
        // not built with a password for. Default handling is what the session does with no delegate
        // at all, which is the right answer for every one of those.
        if (credential == null ||
            !didReceiveChallenge.protectionSpace.isProxy() ||
            didReceiveChallenge.previousFailureCount > 0
        ) {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return
        }
        completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didSendBodyData: Long,
        totalBytesSent: Long,
        totalBytesExpectedToSend: Long,
    ) {
        val listener = onUploadProgress ?: return
        // -1 is `NSURLSessionTransferSizeUnknown`, which a body handed over as `NSData` never is;
        // guarded anyway because dividing by it would report a ring running backwards.
        if (totalBytesExpectedToSend <= 0) return
        listener((totalBytesSent.toDouble() / totalBytesExpectedToSend).toFloat().coerceIn(0f, 1f))
    }
}

private fun HttpBody.contentType(): String? =
    when (this) {
        is HttpBody.Empty -> null
        is HttpBody.Text -> contentType
        is HttpBody.Multipart -> "multipart/form-data; boundary=$MULTIPART_BOUNDARY"
    }

internal fun HttpBody.toNSData(): NSData =
    when (this) {
        is HttpBody.Empty -> ByteArray(0).toNSData()

        is HttpBody.Text -> content.encodeToByteArray().toNSData()

        is HttpBody.Multipart -> {
            // Written out rather than assembled by a Foundation type: `NSURLSession` has no
            // multipart builder of its own, and the format is four lines of it.
            val data = NSMutableData()
            fields.forEach { (name, value) ->
                data.appendData(
                    (
                        "--$MULTIPART_BOUNDARY\r\n" +
                            "Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
                        ).encodeToByteArray().toNSData(),
                )
            }
            data.appendData(
                (
                    "--$MULTIPART_BOUNDARY\r\n" +
                        "Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n" +
                        "Content-Type: $fileMimeType\r\n\r\n"
                    ).encodeToByteArray().toNSData(),
            )
            data.appendData(fileBytes.toNSData())
            data.appendData("\r\n--$MULTIPART_BOUNDARY--\r\n".encodeToByteArray().toNSData())
            data
        }
    }

private const val MULTIPART_BOUNDARY = "----NodysseyFormBoundary7MA4YWxkTrZu0gW"

private fun NSData.decodeUtf8(): String? = NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()
