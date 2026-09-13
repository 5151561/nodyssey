package io.github.plaza.core.net

/**
 * Expires the site's unreplayable cookies the moment an answer sets one.
 *
 * The platform store takes every `Set-Cookie` it is handed — `NSHTTPCookieStorage` on its own,
 * `CookieManager` through the jar beside it — and the next request would carry the cookie back.
 * On Android the jar already drops it on both sides; this is what makes the Apple session, which
 * has no jar to filter in, behave the same. Innermost in the chain, so a retried request is
 * scrubbed between tries. See [SiteConfig.unreplayableCookies].
 *
 * Before the request as well as after the answer, and the first half is not belt-and-braces: an
 * install that already holds the cookie from before this existed would send it, be answered 503 —
 * which sets nothing — and never reach the scrub that runs after an answer *sets* it. One store
 * read per request is what that costs.
 */
class CookieScrubbingTransport(
    private val delegate: HttpTransport,
    private val cookies: SessionCookies,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        cookies.dropUnreplayable()
        val response = delegate.execute(request, onUploadProgress)
        if (cookies.setsUnreplayable(response.header("set-cookie"))) cookies.dropUnreplayable()
        return response
    }
}
