@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.plaza.core.net

import io.github.plaza.core.toNSData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
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
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [HttpTransport] on `NSURLSession` — the Apple side of the one contract everything above the
 * network layer is written against.
 *
 * The session is handed in rather than built here for the same reason the OkHttp side takes a
 * client: the cookie storage, the proxy, the timeouts and the default headers are configured once
 * by whoever assembles the app, and none of them varies per call. [appleUrlSession] is the
 * configuration this app would use, and it is a function rather than a default argument so that a
 * caller has to have looked at it.
 *
 * **The cookie half is not finished here, and the arrangement is not the one Android has.** On
 * Android a single store — `CookieManager` — is both what the sign-in WebView writes to and what
 * OkHttp reads, so [WebViewCookieJar] is pure translation and nothing has to be copied. WebKit has
 * no such store: a `WKWebView` writes to its `WKWebsiteDataStore.httpCookieStore`, which is a
 * different jar from [NSHTTPCookieStorage.sharedHTTPCookieStorage] that `NSURLSession` reads, and
 * its API is asynchronous besides. Threshold A of `docs/kmp-migration-decision.md` proved the round
 * trip *by hand*: read the cookies out of `WKHTTPCookieStore` after signing in, hand them to
 * `URLSession`, get HTTP 200. It did not show the two jars sharing anything on their own.
 *
 * So what this file provides is the `URLSession` side only. Bridging it to WebKit — copying both
 * ways and keeping up with a `WKHTTPCookieStoreObserver` while a challenge is live — is step D3,
 * which is where the `WKWebView` it would observe first exists. Nothing constructs this class or
 * [appleUrlSession] yet for that reason.
 */
class NSUrlSessionTransport(
    private val session: NSURLSession,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val url = NSURL.URLWithString(request.url) ?: throw SiteException(SiteError.Network)
        val call = NSMutableURLRequest(uRL = url)
        call.setHTTPMethod(request.method)
        request.headers.forEach { (name, value) -> call.setValue(value, forHTTPHeaderField = name) }
        request.body?.let { body ->
            body.contentType()?.let { call.setValue(it, forHTTPHeaderField = "Content-Type") }
            call.setHTTPBody(body.toNSData())
        }

        return suspendCancellableCoroutine { continuation ->
            val task =
                session.dataTaskWithRequest(call) { data, response, error ->
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
 * there" — is false; see the class KDoc.
 *
 * The three headers are the ones `BrowserHeadersInterceptor` fills in on the OkHttp side, and each
 * is there because leaving it out has cost this app a visible failure. The [referer] is the one that
 * needs the delegate below: some image hosts serve *only* requests referred by the forum, and others
 * refuse any request that still carries one after a redirect. See [ForumSessionDelegate].
 *
 * @param referer null for a third-party host — an API key's host has no part in the forum's browsing
 *   session and should not be told about it. The forum's own client passes the site's base URL.
 */
fun appleUrlSession(
    userAgent: String,
    acceptLanguage: String,
    referer: String? = null,
    timeoutSeconds: Double = 20.0,
): NSURLSession {
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration
    configuration.HTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage
    configuration.HTTPShouldSetCookies = true
    configuration.timeoutIntervalForRequest = timeoutSeconds
    configuration.HTTPAdditionalHeaders =
        buildMap<Any?, Any?> {
            put("User-Agent", userAgent)
            put("Accept-Language", acceptLanguage)
            if (referer != null) put("Referer", referer)
        }
    return NSURLSession.sessionWithConfiguration(
        configuration,
        delegate = ForumSessionDelegate(),
        delegateQueue = null,
    )
}

/**
 * Drops `Referer` once a redirect has carried a request to another host.
 *
 * The Apple half of `CrossOriginRefererInterceptor`, and the same two hosts are behind it. Hotlink
 * protection is one of them: an image host that answers a direct request with
 * 「只允许将图片嵌入网页」 and a 403 needs the referrer on the first hop. The other is the opposite —
 * `pic1.imgdb.cn` sends `Referrer-Policy: no-referrer` and a 302 to a Baidu CDN that refuses anything
 * still carrying `Referer: nodeseek.com`. A browser drops it on that hop; `NSURLSession`, like OkHttp,
 * carries it through unless told otherwise, and this is where it is told.
 *
 * Host, not origin: an `http` → `https` upgrade of the same host is not the situation this is about,
 * and hotlink protection is keyed on the host anyway.
 *
 * A task delegate rather than anything the caller does: redirects are followed inside the session, so
 * this is the only place a hop that the caller never asked for can be seen. It is still called when
 * the task was created with a completion handler — only the data and response callbacks are the ones
 * that handler takes over.
 */
private class ForumSessionDelegate :
    NSObject(),
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
}

/**
 * [SessionCookieStore] on the cookie jar `NSURLSession` reads — **not** on the one a `WKWebView`
 * writes to. See the [NSUrlSessionTransport] KDoc for why those are two jars on this platform.
 *
 * The storage is a required argument rather than a defaulted one so that D3 has to say which jar it
 * means. It is also why the interface's synchronous [cookieHeader] is still honest here and would
 * not be if this read WebKit directly: `WKHTTPCookieStore.getAllCookies` answers on a callback, so
 * the bridge has to keep a mirror this can read rather than reaching across at call time.
 */
class AppleCookieStore(
    private val storage: NSHTTPCookieStorage,
) : SessionCookieStore {

    override fun cookieHeader(url: String): String? {
        val target = NSURL.URLWithString(url) ?: return null
        val cookies = storage.cookiesForURL(target).orEmpty()
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { cookie ->
            val typed = cookie as platform.Foundation.NSHTTPCookie
            "${typed.name}=${typed.value}"
        }
    }

    override fun setCookie(url: String, cookie: String) {
        val target = NSURL.URLWithString(url) ?: return
        val parsed =
            platform.Foundation.NSHTTPCookie.cookiesWithResponseHeaderFields(
                mapOf<Any?, Any?>("Set-Cookie" to cookie),
                forURL = target,
            )
        storage.setCookies(parsed, forURL = target, mainDocumentURL = null)
    }

    override fun removeAll() {
        storage.cookies.orEmpty().forEach { storage.deleteCookie(it as platform.Foundation.NSHTTPCookie) }
    }

    // `NSHTTPCookieStorage` writes through on its own; there is no batched state to push. Android's
    // `CookieManager` is the one that needs telling, which is why the method exists at all.
    override fun flush() = Unit
}

private fun HttpBody.contentType(): String? =
    when (this) {
        is HttpBody.Empty -> null
        is HttpBody.Text -> contentType
        is HttpBody.Multipart -> "multipart/form-data; boundary=$MULTIPART_BOUNDARY"
    }

private fun HttpBody.toNSData(): NSData =
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
