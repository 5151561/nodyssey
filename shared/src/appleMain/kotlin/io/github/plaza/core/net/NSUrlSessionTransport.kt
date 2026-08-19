@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.plaza.core.net

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
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.appendData
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
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
 * The cookie half is what threshold A of `docs/kmp-migration-plan.md` measured: a `WKWebView`
 * signs in, its cookies land in [NSHTTPCookieStorage.sharedHTTPCookieStorage], and a session
 * configured to use that storage sends them — which is the same arrangement Android reaches through
 * `CookieManager` and [WebViewCookieJar].
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
 * `defaultSessionConfiguration` rather than `ephemeral`: the shared cookie storage is the whole
 * point — see the class KDoc — and the ephemeral one has a storage of its own that the sign-in
 * browser never writes to.
 */
fun appleUrlSession(
    userAgent: String,
    acceptLanguage: String,
    timeoutSeconds: Double = 20.0,
): NSURLSession {
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration
    configuration.HTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage
    configuration.HTTPShouldSetCookies = true
    configuration.timeoutIntervalForRequest = timeoutSeconds
    // The same two headers `BrowserHeadersInterceptor` fills in on Android, and for the same reason:
    // a request without them is a request no browser makes, and Cloudflare scores that.
    configuration.HTTPAdditionalHeaders =
        mapOf<Any?, Any?>("User-Agent" to userAgent, "Accept-Language" to acceptLanguage)
    return NSURLSession.sessionWithConfiguration(configuration)
}

/** [SessionCookieStore] on Apple's own cookie jar, the one a `WKWebView` signs in into. */
class AppleCookieStore(
    private val storage: NSHTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage,
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

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
    }

private fun NSData.decodeUtf8(): String? = NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()
