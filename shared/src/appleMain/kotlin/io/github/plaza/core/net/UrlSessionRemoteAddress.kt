package io.github.plaza.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSURLSessionTaskMetrics
import platform.Foundation.NSURLSessionTaskTransactionMetrics
import platform.Foundation.dataTaskWithURL
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One real request to [url], and the address it was answered from.
 *
 * This is how 测试解析 is answered on a platform whose resolver the app cannot call directly. Android
 * asks its own `Dns` and reads the list it returns; here there is no such object — the resolver is the
 * system's, configured through the privacy context — so the honest equivalent is to *make a request
 * the way the app makes them* and report where it landed. `NSURLSessionTaskTransactionMetrics`
 * carries that as `remoteAddress`, which no other API on this platform exposes.
 *
 * **A session of its own, built from this one's configuration.** The copy carries everything that
 * decides where a request goes — the proxy dictionary, the headers, the cookie storage — while a
 * fresh session brings a fresh connection pool, and that is the point: a request on a pooled
 * connection is answered without resolving anything, which is precisely what this is asked to
 * measure. The session is invalidated afterwards rather than kept; it exists for one request.
 *
 * @return the address, or null when the platform delivered no metrics for the task. The metrics
 *   callback and the task's completion are not documented to arrive in a fixed order, so a null here
 *   means "answered, address unknown" rather than a failure — the caller still knows the request
 *   succeeded and how long it took.
 * @throws SiteException when the request itself failed, which is what a resolver that cannot answer
 *   looks like from up here.
 */
suspend fun NSURLSession.remoteAddressOf(url: String): String? {
    val target = NSURL.URLWithString(url) ?: throw SiteException(SiteError.Network)
    // The proxy password, carried over the same way the bounded read carries it: a probe session gets
    // its own delegate, and a `407` it cannot answer would fail a test of something else entirely.
    val probeDelegate = RemoteAddressDelegate((delegate as? ForumSessionDelegate)?.proxyCredential)
    val probe = NSURLSession.sessionWithConfiguration(configuration, probeDelegate, delegateQueue = null)
    try {
        return suspendCancellableCoroutine { continuation ->
            val task =
                probe.dataTaskWithURL(target) { _, _, error: NSError? ->
                    if (error != null) {
                        continuation.resumeWithException(
                            SiteException(SiteError.Network, detail = error.localizedDescription),
                        )
                    } else {
                        continuation.resume(probeDelegate.remoteAddress)
                    }
                }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    } finally {
        probe.finishTasksAndInvalidate()
    }
}

/**
 * Keeps the address off the task's metrics, and answers a proxy that asks for a password.
 *
 * The last transaction rather than the first: a redirect produces one per hop, and the address worth
 * reporting is the one the answer actually came from.
 */
private class RemoteAddressDelegate(
    proxyCredential: NSURLCredential?,
) : NSObject(),
    NSURLSessionTaskDelegateProtocol {

    private val forum = ForumSessionDelegate(proxyCredential = proxyCredential)

    var remoteAddress: String? = null
        private set

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didFinishCollectingMetrics: NSURLSessionTaskMetrics,
    ) {
        remoteAddress =
            didFinishCollectingMetrics.transactionMetrics
                .filterIsInstance<NSURLSessionTaskTransactionMetrics>()
                .lastOrNull()
                ?.remoteAddress
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) = forum.URLSession(session, task, didReceiveChallenge, completionHandler)

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: platform.Foundation.NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) = forum.URLSession(session, task, willPerformHTTPRedirection, newRequest, completionHandler)
}
