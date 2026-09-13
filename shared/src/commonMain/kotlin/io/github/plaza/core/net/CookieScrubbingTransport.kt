package io.github.plaza.core.net

/**
 * Expires the site's unreplayable cookies the moment an answer sets one.
 *
 * The platform store takes every `Set-Cookie` it is handed — `NSHTTPCookieStorage` on its own,
 * `CookieManager` through the jar beside it — and the next request would carry the cookie back.
 * On Android the jar already drops it on both sides; this is what makes the Apple session, which
 * has no jar to filter in, behave the same, and it costs one header read per answer. Innermost in
 * the chain, so a retried request is scrubbed between tries. See [SiteConfig.unreplayableCookies].
 */
class CookieScrubbingTransport(
    private val delegate: HttpTransport,
    private val cookies: SessionCookies,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        val response = delegate.execute(request, onUploadProgress)
        if (cookies.setsUnreplayable(response.header("set-cookie"))) cookies.dropUnreplayable()
        return response
    }
}
